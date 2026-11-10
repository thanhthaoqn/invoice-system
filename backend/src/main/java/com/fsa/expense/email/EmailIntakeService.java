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

/** Claims and raw retention commit before invoking the document pipeline. */
@Service
public class EmailIntakeService {
    private final EmailMessageRepository messages;
    private final TenantRepository tenants;
    private final FileStorageService storage;
    private final ObjectMapper json;
    private final IntakeService intake;
    private final IntakeLimits limits;
    private final TransactionTemplate tx;
    public EmailIntakeService(EmailMessageRepository messages,TenantRepository tenants,FileStorageService storage,
            ObjectMapper json,IntakeService intake,IntakeLimits limits,PlatformTransactionManager manager) {
        this.messages=messages; this.tenants=tenants; this.storage=storage; this.json=json;
        this.intake=intake; this.limits=limits; this.tx=new TransactionTemplate(manager);
    }
    public List<EmailMessage> history(){return messages.findTop100ByTenantIdOrderByCreatedAtDesc(TenantContext.id());}
    public EmailMessage receive(EmailPayload payload) {
        long total=0;
        for(var attachment:payload.attachments()) {
            total+=attachment.content().length;
            if(attachment.content().length>limits.getMaxFileBytes() || total>limits.getMaxBatchBytes())
                throw AuthService.error(413,"Email vượt giới hạn dung lượng");
        }
        byte[] raw;
        try {raw=json.writeValueAsBytes(payload);} catch(Exception e){throw new IllegalArgumentException("Email không hợp lệ",e);}
        String key=AuditValidationService.computeSha256(payload.messageId()!=null && !payload.messageId().isBlank()
                ? payload.messageId().trim().getBytes(java.nio.charset.StandardCharsets.UTF_8):raw);
        boolean[] claimed={false};
        EmailMessage message=tx.execute(status -> {
            tenants.lockById(TenantContext.id()).orElseThrow();
            var existing=messages.findByTenantIdAndMessageKey(TenantContext.id(),key);
            if(existing.isPresent()) return existing.get();
            EmailMessage next=new EmailMessage(); next.setTenantId(TenantContext.id()); next.setMessageKey(key);
            next.setSender(payload.sender()); next.setSubject(payload.subject());
            // A persistence failure propagates as HTTP 5xx; provider must redeliver.
            next.setRawPath(storage.storeBytes(raw));
            claimed[0]=true;
            return messages.saveAndFlush(next);
        });
        return claimed[0] ? process(message,payload):message;
    }
    public EmailMessage retry(Long id) {
        EmailMessage message=tx.execute(status -> {
            tenants.lockById(TenantContext.id()).orElseThrow();
            EmailMessage next=messages.findByIdAndTenantId(id,TenantContext.id()).orElseThrow(() -> AuthService.error(404,"Không tìm thấy email"));
            boolean stale="RECEIVED".equals(next.getState()) && next.getUpdatedAt().isBefore(Instant.now().minusSeconds(900));
            if(!stale && !Set.of("FAILED","PARTIAL","PAUSE").contains(next.getState())) throw AuthService.error(409,"Email không thể xử lý lại");
            if(next.getAttempts()>=3) throw AuthService.error(409,"Đã đạt giới hạn 3 lần xử lý");
            next.setState("RECEIVED"); next.setUpdatedAt(Instant.now()); return messages.saveAndFlush(next);
        });
        try {return process(message,json.readValue(Files.readAllBytes(storage.getFilePath(message.getRawPath())),EmailPayload.class));}
        catch(java.io.IOException e){message.setState("PAUSE");message.setNote("Không đọc được bản gốc");return messages.save(message);}
    }
    private EmailMessage process(EmailMessage message,EmailPayload payload) {
        message.setAttempts(message.getAttempts()+1);
        var request=((org.springframework.web.context.request.ServletRequestAttributes)org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()).getRequest();
        Object previous=request.getAttribute("emailIntake"); request.setAttribute("emailIntake",true);
        try {
            if(payload.attachments().isEmpty()) {
                message.setState(payload.html()!=null && payload.html().contains("http")?"FAILED":"NO_XML");
                message.setNote("Bản gốc đã lưu. Link portal cần adapter tải an toàn; chưa tự động truy cập URL.");
            } else {
                var result=intake.upload(payload.attachments().stream().map(a -> (MultipartFile)new Source(a)).toList());
                message.setSessionId(result.session().getId());
                long good=result.items().stream().filter(i -> Set.of("INGESTED","ALREADY_RECEIVED","DUPLICATE_IDENTITY","PDF_ATTACHED").contains(i.getState())).count();
                long bad=result.items().stream().filter(i -> !Set.of("INGESTED","ALREADY_RECEIVED","DUPLICATE_IDENTITY","PDF_ATTACHED","ARCHIVE_PROCESSED","OCR_REVIEW").contains(i.getState())).count();
                boolean pdfOnly=result.items().stream().noneMatch(IntakeItem::isXmlDocument) && result.items().stream().anyMatch(i -> "OCR_REVIEW".equals(i.getState()));
                message.setState(bad>0?(good>0?"PARTIAL":"FAILED"):(pdfOnly?"NO_XML":good>0?"SUCCESS":"NO_XML"));
                message.setNote("Chi tiết tài liệu tại phiên nhập #"+message.getSessionId());
            }
        } catch(RuntimeException e) {message.setState("FAILED");message.setNote("Xử lý lỗi; bản gốc còn nguyên để xử lý lại");}
        finally {if(previous==null)request.removeAttribute("emailIntake");else request.setAttribute("emailIntake",previous);}
        message.setUpdatedAt(Instant.now()); return messages.save(message);
    }
    private record Source(EmailPayload.Attachment attachment) implements MultipartFile {
        public String getName(){return "files";} public String getOriginalFilename(){return attachment.name();}
        public String getContentType(){return "application/octet-stream";} public boolean isEmpty(){return getSize()==0;}
        public long getSize(){return attachment.content().length;} public byte[] getBytes(){return attachment.content();}
        public java.io.InputStream getInputStream(){return new java.io.ByteArrayInputStream(getBytes());}
        public void transferTo(java.io.File file)throws java.io.IOException{Files.write(file.toPath(),getBytes());}
    }
}
