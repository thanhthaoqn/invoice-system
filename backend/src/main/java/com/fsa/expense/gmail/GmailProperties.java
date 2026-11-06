package com.fsa.expense.gmail;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.gmail")
@Getter @Setter
public class GmailProperties {
    private boolean enabled;
    private String clientId = "";
    private String clientSecret = "";
    private String redirectUri = "";
    private String tokenEncryptionKey = "";
    private String labelName = "HOA_DON";
    private long syncIntervalMs = 300_000;
    private int pageSize = 100;
    private int maxRetryAttempts = 3;
    private String frontendResultUrl = "http://127.0.0.1:5173/?gmail=";

    public void requireConfigured() {
        if (!enabled || clientId.isBlank() || clientSecret.isBlank() || redirectUri.isBlank()
                || tokenEncryptionKey.isBlank()) {
            throw new IllegalStateException("Tích hợp Gmail chưa được cấu hình đầy đủ");
        }
    }
}
