package com.fsa.expense.email;

import com.fsa.expense.auth.*;
import com.fsa.expense.service.FileStorageService;
import jakarta.mail.*;
import jakarta.mail.search.FlagTerm;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;

@Service
public class EmailPollerService {
    private final MailboxAccountRepository accounts;
    private final TenantRepository tenants;
    private final MailPasswordCipher cipher;
    private final MailContentReader reader;
    private final FileStorageService storage;
    private final EmailIntakeService intake;
    private final EmailMessageRepository messages;
    private final TransactionTemplate tx;
    private final EmailRedisSupport redis;
    public EmailPollerService(MailboxAccountRepository accounts,TenantRepository tenants,MailPasswordCipher cipher,
            MailContentReader reader,FileStorageService storage,EmailIntakeService intake,EmailMessageRepository messages,PlatformTransactionManager manager,EmailRedisSupport redis){
        this.accounts=accounts;this.tenants=tenants;this.cipher=cipher;this.reader=reader;this.storage=storage;this.intake=intake;this.messages=messages;tx=new TransactionTemplate(manager);
        this.redis=redis;
    }
    public record PollResult(int received,int rejected){}
    public PollResult pollCurrent(){
        var account=accounts.findByTenantId(TenantContext.id()).orElseThrow(() -> AuthService.error(400,"Chưa cấu hình hộp thư"));
        if(!account.isActive())throw AuthService.error(409,"Hộp thư đang tắt");
        return poll(account);
    }
    @Scheduled(fixedDelayString="${app.email.poll-interval-ms:900000}",initialDelayString="${app.email.poll-initial-delay-ms:60000}")
    public void scheduled(){
        for(var account:accounts.findByActiveTrue()){
            TenantContext.runAs(account.getTenantId(),() -> {try{poll(account);}catch(RuntimeException ignored){/* Error persisted on account; no credentials in logs. */}});
        }
    }
    private PollResult poll(MailboxAccount account){
        String key="email:poll:"+account.getTenantId();String token=redis.acquire(key,java.time.Duration.ofMinutes(30));
        try{return pollLocked(account);}finally{redis.release(key,token);}
    }
    private PollResult pollLocked(MailboxAccount account){
        String owner=UUID.randomUUID().toString();
        Integer claimed=tx.execute(status -> accounts.claim(account.getId(),Instant.now(),Instant.now().plusSeconds(1800),owner));
        if(claimed==null || claimed!=1)throw AuthService.error(409,"Hộp thư đang được đồng bộ");
        String error=null;
        try {
            Tenant tenant=tenants.findById(account.getTenantId()).orElseThrow();
            if(!"ACTIVE".equals(tenant.getStatus()) || !"ACTIVE".equals(tenant.getSubscriptionStatus()))throw AuthService.error(403,"Doanh nghiệp không hoạt động");
            PublicNetwork.resolve(account.getHost());
            Properties props=new Properties();
            props.put("mail.imaps.ssl.checkserveridentity","true");props.put("mail.imaps.ssl.protocols","TLSv1.3 TLSv1.2");
            props.put("mail.imaps.connectiontimeout","10000");props.put("mail.imaps.timeout","30000");props.put("mail.imaps.writetimeout","30000");
            props.put("mail.imaps.peek","true");
            try(Store store=Session.getInstance(props).getStore("imaps")){
                store.connect(account.getHost(),account.getPort(),account.getEmail(),cipher.decrypt(account.getPasswordEncrypted(),account.getTenantId()));
                Folder folder=store.getFolder("INBOX");folder.open(Folder.READ_WRITE);
                int count=0,rejected=0;
                try {
                    Message[] unread=folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN),false));
                    long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(10);
                    for(Message mail:unread){
                        if(count>=25 || System.nanoTime()>deadline)break;
                        boolean allowed=processMessage(mail,account);count++;if(!allowed)rejected++;
                    }
                }finally {folder.close(false);}
                return new PollResult(count,rejected);
            }
        }catch(Exception e){error=e instanceof jakarta.mail.AuthenticationFailedException?"Xác thực IMAP thất bại; kiểm tra email và mật khẩu ứng dụng":"Đồng bộ thất bại; kiểm tra kết nối, khóa mã hóa hoặc kho lưu trữ. Thư chưa lưu an toàn vẫn chưa đọc.";throw AuthService.error(502,error);}
        finally {String note=error;tx.executeWithoutResult(status -> accounts.release(account.getId(),owner,Instant.now(),note));}
    }
    boolean processMessage(Message mail,MailboxAccount account)throws Exception{
        // Entire RFC822 retained before MIME parsing, and no SEEN on any persistence failure.
        byte[] original=reader.original(mail);String originalPath=storage.storeBytes(original);
        EmailPayload payload=reader.read(mail);
        boolean allowed=MailboxService.senderAllowed(account,payload.sender());
        EmailMessage result=intake.receive(payload,"OWN_IMAP",allowed);
        if(result.getRawPath()==null)throw new IntakePersistException(new IllegalStateException("Missing raw payload"));
        if(result.getOriginalMimePath()==null){result.setOriginalMimePath(originalPath);messages.saveAndFlush(result);}
        mail.setFlag(Flags.Flag.SEEN,true);return allowed;
    }
    @Scheduled(fixedDelayString="${app.email.retry-interval-ms:10000}",initialDelay=60000)
    public void retryDue(){
        messages.findTop50ByDeadLetterTrueOrderByUpdatedAtDesc().forEach(redis::deadLetter);
        for(var message:messages.findTop50ByStateAndNextRetryAtBeforeOrderByNextRetryAt("PAUSE",Instant.now()))
            TenantContext.runAs(message.getTenantId(),() -> {try{intake.retry(message.getId(),true);}catch(RuntimeException ignored){}});
    }
}
