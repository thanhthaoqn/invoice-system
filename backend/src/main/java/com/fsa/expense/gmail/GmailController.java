package com.fsa.expense.gmail;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/gmail")
public class GmailController {
    private final GmailOAuthService oauth;
    private final GmailSyncService sync;
    public GmailController(GmailOAuthService oauth, GmailSyncService sync) { this.oauth=oauth; this.sync=sync; }

    @GetMapping("/status") public GmailOAuthService.Status status(HttpSession session) {
        return oauth.status(user(session),tenant(session));
    }
    @PostMapping("/oauth/authorization-url") public Map<String,String> authorizationUrl(HttpSession session) {
        return Map.of("url",oauth.authorizationUrl(user(session),tenant(session)));
    }
    @PostMapping("/disconnect") public void disconnect(HttpSession session) { oauth.disconnect(user(session),tenant(session)); }
    @PostMapping("/sync") public GmailSyncService.SyncResult sync(HttpSession session) {
        oauth.requireAdmin(user(session),tenant(session)); return sync.syncTenant(tenant(session));
    }
    private Long user(HttpSession session) { return (Long) session.getAttribute("userId"); }
    private Long tenant(HttpSession session) { return (Long) session.getAttribute("tenantId"); }
}
