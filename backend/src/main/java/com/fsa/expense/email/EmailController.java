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
    private final Set<String> senders;
    public EmailController(EmailIntakeService intake,TenantRepository tenants,
            @Value("${app.email.webhook-secret:}") String secret,
            @Value("${app.email.tenant-id:0}") Long tenantId,
            @Value("${app.email.allowed-senders:}") String senders) {
        this.intake=intake;this.tenants=tenants;this.secret=secret;this.tenantId=tenantId;
        this.senders=new HashSet<>();
        Arrays.stream(senders.split(",")).map(String::trim).filter(s -> !s.isBlank()).map(s -> s.toLowerCase(Locale.ROOT)).forEach(this.senders::add);
    }
    @PostMapping("/emails/webhook")
    public EmailMessage webhook(@RequestHeader(value="X-Inbot-Email-Webhook-Secret",defaultValue="") String supplied,
            @Valid @RequestBody EmailPayload payload,HttpServletRequest request) {
        if(secret.isBlank() || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))
            throw AuthService.error(401,"Webhook secret không hợp lệ");
        Tenant tenant=tenants.findById(tenantId).orElseThrow(() -> AuthService.error(503,"Chưa cấu hình doanh nghiệp nhận email"));
        if(!"ACTIVE".equals(tenant.getStatus()) || !"ACTIVE".equals(tenant.getSubscriptionStatus())) throw AuthService.error(403,"Doanh nghiệp không hoạt động");
        if(!senders.contains(payload.sender().toLowerCase(Locale.ROOT))) throw AuthService.error(403,"REJECTED_SENDER");
        request.setAttribute("tenantId",tenantId); request.setAttribute("username","email-webhook");
        return intake.receive(payload);
    }
    @GetMapping("/api/emails") public List<EmailMessage> history(){return intake.history();}
    @PostMapping("/api/emails/{id}/reprocess") public EmailMessage retry(@PathVariable Long id){return intake.retry(id);}
}
