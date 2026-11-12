package com.fsa.expense.email;

import com.fsa.expense.auth.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@RestController
public class EmailController {
    private final EmailIntakeService intake;
    private final TenantRepository tenants;
    private final String secret;
    private final Long tenantId;
    private final MailboxService mailboxes;
    private final EmailPollerService poller;
    private final SystemInboxService systemInbox;
    public EmailController(EmailIntakeService intake,TenantRepository tenants,
            @Value("${app.email.webhook-secret:}") String secret,
            @Value("${app.email.tenant-id:0}") Long tenantId,
            MailboxService mailboxes,EmailPollerService poller,SystemInboxService systemInbox) {
        this.intake=intake;this.tenants=tenants;this.secret=secret;this.tenantId=tenantId;
        this.mailboxes=mailboxes;this.poller=poller;
        this.systemInbox=systemInbox;
    }
    @PostMapping("/emails/webhook")
    public EmailMessage webhook(@RequestHeader(value="X-Inbot-Email-Webhook-Secret",defaultValue="") String supplied,
            @Valid @RequestBody EmailPayload payload,HttpServletRequest request) {
        if(secret.isBlank() || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))
            throw AuthService.error(401,"Webhook secret không hợp lệ");
        Tenant tenant=tenants.findById(tenantId).orElseThrow(() -> AuthService.error(503,"Chưa cấu hình doanh nghiệp nhận email"));
        if(!"ACTIVE".equals(tenant.getStatus()) || !"ACTIVE".equals(tenant.getSubscriptionStatus())) throw AuthService.error(403,"Doanh nghiệp không hoạt động");
        request.setAttribute("tenantId",tenantId); request.setAttribute("username","email-webhook");
        return intake.receive(payload);
    }
    @GetMapping("/api/emails") public List<EmailMessage> history(){return intake.history();}
    @PostMapping("/api/emails/{id}/reprocess") public EmailMessage retry(@PathVariable Long id){return intake.retry(id);}
    @GetMapping("/api/emails/mailbox") public MailboxService.View mailbox(){return mailboxes.get();}
    @PutMapping("/api/emails/mailbox") public MailboxAccount saveMailbox(@Valid @RequestBody MailboxService.Settings settings){return mailboxes.save(settings);}
    @PostMapping("/api/emails/sync") public EmailPollerService.PollResult sync(){return poller.pollCurrent();}
    @GetMapping("/api/emails/system-mailbox") public SystemInboxService.View systemMailbox(){return systemInbox.current();}
    @PostMapping("/api/emails/system-mailbox") public TenantMailbox provisionSystemMailbox(){return systemInbox.provision();}
}
