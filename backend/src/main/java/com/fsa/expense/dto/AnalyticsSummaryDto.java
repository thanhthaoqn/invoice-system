package com.fsa.expense.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsSummaryDto {
    private BigDecimal totalApprovedSpend;
    private long totalInvoicesCount;
    private long pendingReviewCount;
    private long approvedCount;
    private long rejectedCount;
    private double aiAccuracyRate; // %
    private long totalErrorLogsCount;

    @Builder.Default
    private List<CategorySpendDto> categorySpends = new ArrayList<>();

    @Builder.Default
    private List<VendorSpendDto> topVendors = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategorySpendDto {
        private String category;
        private BigDecimal amount;
        private long invoiceCount;
        private double percentage;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VendorSpendDto {
        private String vendorName;
        private BigDecimal amount;
        private long invoiceCount;
    }
}
