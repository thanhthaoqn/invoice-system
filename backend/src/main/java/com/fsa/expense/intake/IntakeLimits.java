package com.fsa.expense.intake;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.*;

@Component @ConfigurationProperties(prefix="app.intake") @Getter @Setter
public class IntakeLimits {
    private int maxFiles = 20;
    private long maxFileBytes = 15 * 1024 * 1024;
    private long maxBatchBytes = 24 * 1024 * 1024;
    private int maxEntries = 200;
    private long maxExpandedBytes = 50 * 1024 * 1024;
    private int maxCompressionRatio = 100;
    private int maxAttempts = 3;
    private int maxPdfPages = 20;
}
