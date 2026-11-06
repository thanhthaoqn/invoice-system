package com.fsa.expense.gmail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fsa.expense.auth.AuthService;
import com.fsa.expense.service.AuditValidationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
public class GmailSyncService {
    private final GmailProperties properties;
    private final GmailConnectionRepository connections;
    private final GmailMessageRepository messages;
    private final GmailAttachmentRepository attachments;
    private final GoogleOAuthClient oauth;
    private final GmailApiClient gmail;
    private final GmailMimeExtractor mime;
    private final GmailIntakeBridge bridge;
    private final TokenCipher cipher;

    public GmailSyncService(GmailProperties properties,GmailConnectionRepository connections,GmailMessageRepository messages,
            GmailAttachmentRepository attachments,GoogleOAuthClient oauth,GmailApiClient gmail,GmailMimeExtractor mime,
            GmailIntakeBridge bridge,TokenCipher cipher) {
        this.properties=properties; this.connections=connections; this.messages=messages; this.attachments=attachments;
        this.oauth=oauth; this.gmail=gmail; this.mime=mime; this.bridge=bridge; this.cipher=cipher;
    }

    @Scheduled(fixedDelayString="${app.gmail.sync-interval-ms:300000}")
    public void scheduledSync() {
        if (!properties.isEnabled()) return;
        for (GmailConnection connection : connections.findByState("ACTIVE")) {
            try { sync(connection); } catch (RuntimeException ignored) { /* Error is persisted per connection. */ }
        }
    }

    public SyncResult syncTenant(Long tenantId) {
        GmailConnection connection=connections.findByTenantId(tenantId).filter(c -> "ACTIVE".equals(c.getState()))
            .orElseThrow(() -> AuthService.error(409,"Doanh nghiệp chưa kết nối Gmail"));
        return sync(connection);
    }

    public SyncResult sync(GmailConnection connection) {
        properties.requireConfigured(); int discovered=0,processed=0,failed=0;
        try {
            String refresh=cipher.decrypt(connection.getEncryptedRefreshToken());
            String access=oauth.refresh(refresh).accessToken();
            String labelId=resolveLabel(access,connection);
            String page=null;
            do {
                JsonNode list=gmail.messages(access,labelId,page,properties.getPageSize());
                for (JsonNode ref : list.path("messages")) {
                    discovered++;
                    try { if (syncMessage(connection,access,ref.path("id").asText())) processed++; }
                    catch (RuntimeException e) { failed++; recordMessageFailure(connection,ref.path("id").asText(),e); }
                }
                page=list.path("nextPageToken").asText(null);
            } while(page!=null && !page.isBlank());
            connection.setLastSyncAt(Instant.now()); connection.setLastError(null); connections.save(connection);
            return new SyncResult(discovered,processed,failed,connection.getLastSyncAt());
        } catch (RuntimeException e) {
            connection.setLastError(shortError(e)); connection.setLastSyncAt(Instant.now()); connections.save(connection); throw e;
        }
    }

    private String resolveLabel(String access,GmailConnection connection) {
        if (connection.getLabelId()!=null && !connection.getLabelId().isBlank()) return connection.getLabelId();
        for (JsonNode label : gmail.labels(access).path("labels")) {
            if (properties.getLabelName().equals(label.path("name").asText())) {
                connection.setLabelId(label.path("id").asText()); connections.save(connection); return connection.getLabelId();
            }
        }
        throw new IllegalStateException("Không tìm thấy nhãn Gmail "+properties.getLabelName());
    }

    private boolean syncMessage(GmailConnection connection,String access,String gmailId) {
        if (gmailId==null || gmailId.isBlank()) return false;
        GmailMessage row=messages.findByConnectionIdAndGmailMessageId(connection.getId(),gmailId).orElseGet(() -> {
            GmailMessage value=new GmailMessage(); value.setTenantId(connection.getTenantId()); value.setConnectionId(connection.getId());
            value.setGmailMessageId(gmailId); return messages.save(value);
        });
        if ("COMPLETED".equals(row.getState())) return false;
        if (row.getNextRetryAt()!=null && row.getNextRetryAt().isAfter(Instant.now())) return false;
        GmailMimeExtractor.MessageData data=mime.read(gmail.message(access,gmailId));
        row.setThreadId(data.threadId()); row.setRfcMessageId(trim(data.rfcMessageId(),1000)); row.setSender(trim(data.sender(),1000));
        row.setSubject(trim(data.subject(),1000)); row.setReceivedAt(data.receivedAt()); row.setState("DOWNLOADING"); messages.save(row);
        List<GmailAttachment> pending=new ArrayList<>(); List<GmailIntakeBridge.Document> documents=new ArrayList<>();
        for (GmailMimeExtractor.Part part : data.parts()) {
            GmailAttachment attachment=attachments.findByMessageIdAndPartKey(row.getId(),part.key()).orElseGet(() -> {
                GmailAttachment value=new GmailAttachment(); value.setTenantId(connection.getTenantId()); value.setMessageId(row.getId()); value.setPartKey(part.key()); return value;
            });
            if (attachment.getIntakeItemId()!=null) continue;
            byte[] bytes=part.attachmentId()!=null ? gmail.attachment(access,gmailId,part.attachmentId()) : GmailApiClient.decode(part.inlineData());
            attachment.setFileName(trim(part.fileName(),1000)); attachment.setMimeType(trim(part.mimeType(),200)); attachment.setFileSize(bytes.length);
            attachment.setContentHash(AuditValidationService.computeSha256(bytes)); attachment.setState("DOWNLOADED"); attachment.setLastError(null);
            pending.add(attachments.save(attachment)); documents.add(new GmailIntakeBridge.Document(part.fileName(),bytes));
        }
        if (!documents.isEmpty()) {
            var results=bridge.ingest(connection.getTenantId(),connection.getEmailAddress(),documents);
            for(int i=0;i<results.size();i++) {
                var result=results.get(i); var attachment=pending.get(i); attachment.setIntakeItemId(result.itemId());
                attachment.setInvoiceId(result.invoiceId()); attachment.setState(result.state()); attachments.save(attachment);
            }
        }
        row.setState("COMPLETED"); row.setLastError(null); row.setNextRetryAt(null); messages.save(row); return true;
    }

    private void recordMessageFailure(GmailConnection connection,String gmailId,RuntimeException error) {
        GmailMessage row=messages.findByConnectionIdAndGmailMessageId(connection.getId(),gmailId).orElseGet(() -> {
            GmailMessage value=new GmailMessage(); value.setTenantId(connection.getTenantId()); value.setConnectionId(connection.getId()); value.setGmailMessageId(gmailId); return value;
        });
        row.setAttempts(row.getAttempts()+1); row.setLastError(shortError(error));
        if (row.getAttempts()>=properties.getMaxRetryAttempts()) { row.setState("FAILED"); row.setNextRetryAt(null); }
        else { row.setState("RETRY"); row.setNextRetryAt(Instant.now().plus(Duration.ofMinutes((long)Math.pow(5,row.getAttempts()-1)))); }
        messages.save(row);
    }
    private String shortError(Throwable error) { String value=error.getMessage()==null ? error.getClass().getSimpleName() : error.getMessage(); return trim(value,1000); }
    private String trim(String value,int max) { return value==null ? null : value.substring(0,Math.min(max,value.length())); }
    public record SyncResult(int discovered,int processed,int failed,Instant completedAt) {}
}
