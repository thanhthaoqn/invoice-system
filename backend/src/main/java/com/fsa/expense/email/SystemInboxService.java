package com.fsa.expense.email;

import com.fsa.expense.auth.*;
import com.fsa.expense.service.*;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.FlagTerm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;

@Service
public class SystemInboxService {
    private final TenantMailboxRepository routes;
    private final MailboxAccountRepository leases;
    private final UnmatchedSystemEmailRepository unmatched;
    private final TenantRepository tenants;
    private final MailContentReader reader;
    private final FileStorageService storage;
    private final EmailIntakeService intake;
    private final EmailMessageRepository messages;
    private final TransactionTemplate tx;
    private final String domain,host,user,password;
    public SystemInboxService(TenantMailboxRepository routes,MailboxAccountRepository leases,UnmatchedSystemEmailRepository unmatched,TenantRepository tenants,
            MailContentReader reader,FileStorageService storage,EmailIntakeService intake,EmailMessageRepository messages,PlatformTransactionManager manager,
            @Value("${app.email.system-domain:}") String domain,@Value("${app.email.system-host:imap.gmail.com}") String host,
            @Value("${app.email.system-user:}") String user,@Value("${app.email.system-password:}") String password){
        this.routes=routes;this.leases=leases;this.unmatched=unmatched;this.tenants=tenants;this.reader=reader;this.storage=storage;this.intake=intake;this.messages=messages;
        tx=new TransactionTemplate(manager);this.domain=domain.trim().toLowerCase(Locale.ROOT);this.host=host;this.user=user;this.password=password;
    }
    public record View(boolean configured,TenantMailbox mailbox){}
    public View current(){return new View(!domain.isBlank()&&!user.isBlank()&&!password.isBlank(),routes.findByTenantId(TenantContext.id()).orElse(null));}
    public TenantMailbox provision(){
        if(domain.isBlank() || user.isBlank() || password.isBlank())throw AuthService.error(503,"Hộp thư hệ thống chưa được quản trị cấu hình");
        return tx.execute(status -> {
            var tenant=tenants.lockById(TenantContext.id()).orElseThrow();
            return routes.findByTenantId(tenant.getId()).orElseGet(() -> {
                TenantMailbox route=new TenantMailbox();route.setTenantId(tenant.getId());
                // Server-generated local part prevents tenants claiming another company's address.
                route.setAddress("tenant-"+tenant.getId()+"@"+domain);return routes.saveAndFlush(route);
            });
        });
    }
    public Optional<Long> route(Message message)throws Exception{
        for(String header:List.of("Delivered-To","X-Original-To","To")){
            String[] values=message.getHeader(header);if(values==null)continue;
            Set<Long> matches=new HashSet<>();
            for(String value:values)for(var address:InternetAddress.parseHeader(value,false))
                routes.findByAddressAndActiveTrue(address.getAddress().toLowerCase(Locale.ROOT)).ifPresent(route -> matches.add(route.getTenantId()));
            if(!matches.isEmpty())return matches.size()==1?Optional.of(matches.iterator().next()):Optional.empty();
        }
        return Optional.empty();
    }
    @Scheduled(fixedDelayString="${app.email.system-poll-interval-ms:120000}",initialDelay=60000)
    public void poll(){
        if(domain.isBlank() || user.isBlank() || password.isBlank())return;
        MailboxAccount lease=tx.execute(status -> {
            // A tenant row serializes initialization across nodes without relying on JVM locks.
            var tenant=tenants.findAll().stream().findFirst();if(tenant.isEmpty())return null;
            tenants.lockById(tenant.get().getId()).orElseThrow();
            return leases.findByTenantId(0L).orElseGet(() -> {var a=new MailboxAccount();a.setTenantId(0L);a.setActive(false);return leases.saveAndFlush(a);});
        });
        if(lease==null)return;
        String owner=UUID.randomUUID().toString();
        if(!Integer.valueOf(1).equals(tx.execute(status -> leases.claim(lease.getId(),Instant.now(),Instant.now().plusSeconds(1800),owner))))return;
        String error=null;
        try {
            PublicNetwork.resolve(host);
            Properties props=new Properties();props.put("mail.imaps.ssl.checkserveridentity","true");props.put("mail.imaps.peek","true");
            props.put("mail.imaps.connectiontimeout","10000");props.put("mail.imaps.timeout","30000");props.put("mail.imaps.writetimeout","30000");
            try(Store store=Session.getInstance(props).getStore("imaps")){
                store.connect(host,993,user,password);Folder inbox=store.getFolder("INBOX");inbox.open(Folder.READ_WRITE);
                try {
                    int count=0;long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(10);
                    for(Message mail:inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN),false))){
                        if(count++>=25 || System.nanoTime()>deadline)break;
                        byte[] original=reader.original(mail);String originalPath=storage.storeBytes(original);
                        var target=route(mail);
                        if(target.isEmpty()){
                            String hash=AuditValidationService.computeSha256(original);
                            if(!unmatched.existsByMessageHash(hash)){var quarantine=new UnmatchedSystemEmail();quarantine.setMessageHash(hash);quarantine.setOriginalPath(originalPath);quarantine.setReason("No unique matching tenant mailbox");unmatched.saveAndFlush(quarantine);}
                        }else{
                            Tenant tenant=tenants.findById(target.get()).orElseThrow();
                            if(!"ACTIVE".equals(tenant.getStatus()) || !"ACTIVE".equals(tenant.getSubscriptionStatus()))continue;
                            EmailPayload payload=reader.read(mail);
                            TenantContext.runAs(target.get(),() -> {
                                EmailMessage result=intake.receive(payload,"SYSTEM_MAILBOX",true);
                                if(result.getOriginalMimePath()==null){result.setOriginalMimePath(originalPath);messages.saveAndFlush(result);}
                            });
                        }
                        mail.setFlag(Flags.Flag.SEEN,true);
                    }
                }finally{inbox.close(false);}
            }
        }catch(Exception e){error="System inbox poll failed; unretained messages remain unread";}
        finally{String note=error;tx.executeWithoutResult(status -> leases.release(lease.getId(),owner,Instant.now(),note));}
    }
}
