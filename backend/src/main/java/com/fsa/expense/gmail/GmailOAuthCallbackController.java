package com.fsa.expense.gmail;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
public class GmailOAuthCallbackController {
    private final GmailOAuthService oauth;
    private final GmailProperties properties;
    public GmailOAuthCallbackController(GmailOAuthService oauth,GmailProperties properties) { this.oauth=oauth; this.properties=properties; }

    @GetMapping("/oauth/gmail/callback")
    public ResponseEntity<Void> callback(@RequestParam(required=false) String state,
            @RequestParam(required=false) String code, @RequestParam(required=false) String error) {
        String result="connected";
        if (error != null || code == null) result="denied";
        else { oauth.callback(state,code); }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(properties.getFrontendResultUrl()+result)).build();
    }
}
