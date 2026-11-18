package com.fsa.expense.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsa.expense.auth.*;
import com.fsa.expense.intake.*;
import com.fsa.expense.service.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;

/** Durable claim and original payload commit before any parsing or SEEN update. */
@Service
public class EmailIntakeService {
    private final EmailMessageRepository messages;
    private final TenantRepository tenants;
    private final FileStorageService storage;
    private final ObjectMapper json;
    private final IntakeService intake;
    private final IntakeLimits limits;
    private final InvoiceLinkDownloader downloader;
    private final MailboxAccountRepository mailboxes;
    private final EmailRedisSupport redis;
    private final TransactionTemplate tx;
    public EmailIntakeService(EmailMessageRepository messages,TenantRepository tenants,FileStorageService storage,
            ObjectMapper json,IntakeService intake,IntakeLimits limits,PlatformTransactionManager manager,
            InvoiceLinkDownloader downloader,MailboxAccountRepository mailboxes,EmailRedisSupport redis) {
        this.messages=messages;this.tenants=tenants;this.storage=storage;this.json=json;
        this.intake=intake;this.limits=limits;this.tx=new TransactionTemplate(manager);this.downloader=downloader;this.mailboxes=mailboxes;
        this.redis=redis;
    }
    public List<EmailMessage> history(){return messages.findTop100ByTenantIdOrderByCreatedAtDesc(TenantContext.id());}
    public EmailMessage receive(EmailPayload payload){return receive(payload,"WEBHOOK",true);}
    public EmailMessage receive(EmailPayload payload,String channel,boolean senderAllowed) {
        if(payload.attachments().size()>limits.getMaxFiles())throw AuthService.error(413,"Số tệp đính kèm vượt giới hạn");
        long total=0;
        for(var attachment:payload.attachments()) {
            total+=attachment.content().length;
            if(attachment.content().length>limits.getMaxFileBytes() || total>limits.getMaxBatchBytes())
                throw AuthService.error(413,"Email vượt giới hạn dung lượng đính kèm");
        }
        byte[] raw;
        try {raw=json.writeValueAsBytes(payload);}catch(Exception e){throw new IntakePersistException(e);}
        String key=AuditValidationService.computeSha256(payload.messageId()!=null && !payload.messageId().isBlank()
                ? payload.messageId().trim().getBytes(java.nio.charset.StandardCharsets.UTF_8):raw);
        boolean[] claimed={false};
        EmailMessage message=tx.execute(status -> {
            var tenant=tenants.lockById(TenantContext.id()).orElseThrow();
            if(!"ACTIVE".equals(tenant.getStatus()) || !"ACTIVE".equals(tenant.getSubscriptionStatus()))throw AuthService.error(403,"Tenant is inactive");
            var existing=messages.findByTenantIdAndMessageKey(TenantContext.id(),key);
            if(existing.isPresent())return existing.get();
            EmailMessage next=new EmailMessage();next.setTenantId(TenantContext.id());next.setMessageKey(key);
            next.setSender(payload.sender());next.setSubject(payload.subject());next.setChannel(channel);
            try {next.setRawPath(storage.storeBytes(raw));}catch(RuntimeException e){throw new IntakePersistException(e);}
            if(!senderAllowed){next.setState("REJECTED_SENDER");next.setNote("Người gửi không thuộc danh sách được phép của hộp thư");}
            else next.setAttempts(1);
            claimed[0]=true;return messages.saveAndFlush(next);
        });
        return claimed[0] && senderAllowed?process(message,payload):message;
    }
    public EmailMessage retry(Long id){return retry(id,false);}
    public EmailMessage retry(Long id,boolean automatic) {
        String key="retry:"+TenantContext.id()+":"+id;
        String token=redis.acquire(key,java.time.Duration.ofMinutes(30));
        try{return retryClaimed(id,automatic);}finally{redis.release(key,token);}
    }
    private EmailMessage retryClaimed(Long id,boolean automatic) {
        EmailMessage message=tx.execute(status -> {
            var tenant=tenants.lockById(TenantContext.id()).orElseThrow();
            if(!"ACTIVE".equals(tenant.getStatus()) || !"ACTIVE".equals(tenant.getSubscriptionStatus()))throw AuthService.error(403,"Tenant is inactive");
            EmailMessage next=messages.findByIdAndTenantId(id,TenantContext.id()).orElseThrow(() -> AuthService.error(404,"Email not found"));
            boolean stale="RECEIVED".equals(next.getState()) && next.getUpdatedAt().isBefore(Instant.now().minusSeconds(900));
            if(!stale && !Set.of("FAILED","PARTIAL","PAUSE","REJECTED_SENDER").contains(next.getState()))throw AuthService.error(409,"Email is already processing or cannot be retried");
            if(automatic && (!"PAUSE".equals(next.getState()) || next.getNextRetryAt()==null || next.getNextRetryAt().isAfter(Instant.now()) || next.getRetryCount()>=3))throw AuthService.error(409,"Email is not due for retry");
            if(automatic)next.setRetryCount(next.getRetryCount()+1);else next.setReprocessCount(next.getReprocessCount()+1);
            next.setAttempts(next.getAttempts()+1);next.setDeadLetter(false);next.setNextRetryAt(null);
            next.setState("RECEIVED");next.setUpdatedAt(Instant.now());return messages.saveAndFlush(next);
        });
        if("OWN_IMAP".equals(message.getChannel())) {
            var mailbox=mailboxes.findByTenantId(message.getTenantId());
            if(mailbox.isEmpty() || !MailboxService.senderAllowed(mailbox.get(),message.getSender())){
                message.setState("REJECTED_SENDER");return messages.save(message);
            }
        }
        try {return process(message,json.readValue(Files.readAllBytes(storage.getFilePath(message.getRawPath())),EmailPayload.class));}
        catch(java.io.IOException e){return pause(message,"Chưa đọc được bản gốc đã lưu");}
    }
    private EmailMessage process(EmailMessage message,EmailPayload payload) {
        TenantContext.withEmail(() -> {
            try {
                List<EmailPayload.Attachment> files=new ArrayList<>(payload.attachments());
                boolean hasXml=files.stream().anyMatch(a -> a.name().toLowerCase(Locale.ROOT).endsWith(".xml") || a.name().toLowerCase(Locale.ROOT).endsWith(".zip"));
                if(!hasXml){
                    java.io.IOException transientFailure=null;
                    boolean unsupportedLink=false;
                    for(var uri:downloader.extract(payload.html())){
                        if(files.size()>=limits.getMaxFiles())break;
                        EmailPayload.Attachment downloaded;
                        try {downloaded=downloader.download(uri);}
                        catch(java.io.IOException e){transientFailure=e;continue;}
                        catch(IllegalArgumentException e){unsupportedLink=true;continue;}
                        long total=downloaded.content().length+files.stream().mapToLong(a -> a.content().length).sum();
                        if(total>limits.getMaxBatchBytes())throw new IllegalArgumentException("Tổng dung lượng tệp tải về vượt giới hạn");
                        files.add(downloaded);
                        if(downloaded.name().endsWith(".xml") || downloaded.name().endsWith(".zip"))break;
                    }
                    if(files.isEmpty() && transientFailure!=null)throw transientFailure;
                    if(files.isEmpty() && unsupportedLink){message.setState("FAILED");message.setNote("Các link trong email trả về trang tra cứu hoặc tệp chưa được hỗ trợ; cần link tải XML/ZIP/PDF trực tiếp");message.setUpdatedAt(Instant.now());messages.save(message);return;}
                }
                if(files.isEmpty()){
                    message.setState(payload.html()!=null && payload.html().contains("http")?"FAILED":"NO_XML");
                    message.setNote("Không có tệp hóa đơn hoặc link tải trực tiếp được hỗ trợ");
                }else{
                    var result=intake.upload(files.stream().map(a -> (MultipartFile)new Source(a)).toList());
                    message.setSessionId(result.session().getId());
                    long good=result.items().stream().filter(i -> Set.of("INGESTED","ALREADY_RECEIVED","DUPLICATE_IDENTITY","PDF_ATTACHED","OCR_REVIEW","OCR_PENDING").contains(i.getState())).count();
                    long bad=result.items().stream().filter(i -> !Set.of("INGESTED","ALREADY_RECEIVED","DUPLICATE_IDENTITY","PDF_ATTACHED","ARCHIVE_PROCESSED","OCR_REVIEW","OCR_PENDING").contains(i.getState())).count();
                    boolean review=result.items().stream().noneMatch(IntakeItem::isXmlDocument) && result.items().stream().anyMatch(i -> Set.of("OCR_REVIEW","OCR_PENDING").contains(i.getState()));
                    message.setState(bad>0?(good>0?"PARTIAL":"FAILED"):(review?"NEEDS_REVIEW":good>0?"SUCCESS":"NO_XML"));
                    String details=result.items().stream().filter(i -> !Set.of("ARCHIVE_PROCESSED","PDF_ATTACHED").contains(i.getState())).map(i -> i.getFileName()+": "+i.getState()).collect(java.util.stream.Collectors.joining("; "));
                    message.setNote(details.substring(0,Math.min(details.length(),1000)));
                    if(good==0 && result.items().stream().anyMatch(i -> "QUOTA_EXCEEDED".equals(i.getState()))){pause(message,"Đã đạt hạn mức hóa đơn email trong tháng");return;}
                }
                message.setNextRetryAt(null);message.setUpdatedAt(Instant.now());messages.save(message);
            }catch(InvoiceLinkDownloader.TransientDownloadException | java.net.SocketTimeoutException e){pause(message,"Tải hóa đơn tạm thời gián đoạn; hệ thống sẽ thử lại");}
            catch(java.io.IOException e){pause(message,"Lỗi mạng khi tải hóa đơn; hệ thống sẽ thử lại");}
            catch(RuntimeException e){message.setState("FAILED");message.setNote("Xử lý hóa đơn thất bại; bản gốc vẫn được giữ để xử lý lại");message.setUpdatedAt(Instant.now());messages.save(message);}
        });
        return messages.findByIdAndTenantId(message.getId(),message.getTenantId()).orElseThrow();
    }
    private EmailMessage pause(EmailMessage message,String note){
        message.setNote(note);message.setUpdatedAt(Instant.now());
        if(message.getRetryCount()>=3){message.setState("FAILED");message.setDeadLetter(true);message.setNextRetryAt(null);}
        else {message.setState("PAUSE");long[] seconds={10,60,900};message.setNextRetryAt(Instant.now().plusSeconds(seconds[message.getRetryCount()]));}
        EmailMessage saved=messages.save(message);if(saved.isDeadLetter())redis.deadLetter(saved);return saved;
    }
    private record Source(EmailPayload.Attachment attachment) implements MultipartFile {
        public String getName(){return "files";}public String getOriginalFilename(){return attachment.name();}
        public String getContentType(){return "application/octet-stream";}public boolean isEmpty(){return getSize()==0;}
        public long getSize(){return attachment.content().length;}public byte[] getBytes(){return attachment.content();}
        public java.io.InputStream getInputStream(){return new java.io.ByteArrayInputStream(getBytes());}
        public void transferTo(java.io.File file)throws java.io.IOException{Files.write(file.toPath(),getBytes());}
    }
}
