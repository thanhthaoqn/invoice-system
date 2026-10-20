package com.fsa.expense.service;

import com.fsa.expense.dto.AnalyticsSummaryDto;
import com.fsa.expense.dto.ErrorLogDto;
import com.fsa.expense.entity.ErrorLog;
import com.fsa.expense.entity.InvoiceStatus;
import com.fsa.expense.repository.ErrorLogRepository;
import com.fsa.expense.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AnalyticsService {

    private final InvoiceRepository invoiceRepository;
    private final ErrorLogRepository errorLogRepository;

    public AnalyticsService(InvoiceRepository invoiceRepository, ErrorLogRepository errorLogRepository) {
        this.invoiceRepository = invoiceRepository;
        this.errorLogRepository = errorLogRepository;
    }

    public AnalyticsSummaryDto getDashboardSummary() {
        BigDecimal totalSpend = invoiceRepository.sumApprovedTotalAmount();
        if (totalSpend == null) totalSpend = BigDecimal.ZERO;

        long totalCount = invoiceRepository.count();
        long pendingCount = invoiceRepository.countByStatus(InvoiceStatus.PENDING_REVIEW);
        long approvedCount = invoiceRepository.countByStatus(InvoiceStatus.APPROVED);
        long rejectedCount = invoiceRepository.countByStatus(InvoiceStatus.REJECTED);
        long errorLogsCount = errorLogRepository.count();

        // Calculate AI Accuracy Rate
        double accuracyRate = 100.0;
        if (approvedCount > 0) {
            // Distinct invoices that had errors logged
            long errorInvoicesCount = errorLogRepository.findAll().stream()
                    .map(ErrorLog::getInvoiceId)
                    .distinct()
                    .count();
            double ratio = (double) (approvedCount - Math.min(approvedCount, errorInvoicesCount)) / approvedCount;
            accuracyRate = Math.max(0.0, Math.min(100.0, ratio * 100.0));
        }

        // Category breakdown
        List<AnalyticsSummaryDto.CategorySpendDto> categoryList = new ArrayList<>();
        List<Object[]> categoryData = invoiceRepository.getSpendByCategory();
        for (Object[] row : categoryData) {
            String category = (String) row[0];
            BigDecimal amount = (BigDecimal) row[1];
            long count = ((Number) row[2]).longValue();
            double pct = totalSpend.compareTo(BigDecimal.ZERO) > 0 ?
                    amount.divide(totalSpend, 4, RoundingMode.HALF_UP).doubleValue() * 100.0 : 0.0;

            categoryList.add(AnalyticsSummaryDto.CategorySpendDto.builder()
                    .category(category != null ? category : "Khác")
                    .amount(amount)
                    .invoiceCount(count)
                    .percentage(Math.round(pct * 10.0) / 10.0)
                    .build());
        }

        // Top Vendors
        List<AnalyticsSummaryDto.VendorSpendDto> vendorList = new ArrayList<>();
        List<Object[]> vendorData = invoiceRepository.getTopVendors();
        int limit = Math.min(vendorData.size(), 5);
        for (int i = 0; i < limit; i++) {
            Object[] row = vendorData.get(i);
            String vendor = (String) row[0];
            BigDecimal amount = (BigDecimal) row[1];
            long count = ((Number) row[2]).longValue();

            vendorList.add(AnalyticsSummaryDto.VendorSpendDto.builder()
                    .vendorName(vendor != null ? vendor : "Chưa rõ")
                    .amount(amount)
                    .invoiceCount(count)
                    .build());
        }

        return AnalyticsSummaryDto.builder()
                .totalApprovedSpend(totalSpend)
                .totalInvoicesCount(totalCount)
                .pendingReviewCount(pendingCount)
                .approvedCount(approvedCount)
                .rejectedCount(rejectedCount)
                .totalErrorLogsCount(errorLogsCount)
                .aiAccuracyRate(Math.round(accuracyRate * 10.0) / 10.0)
                .categorySpends(categoryList)
                .topVendors(vendorList)
                .build();
    }

    public List<ErrorLogDto> getAllErrorLogs() {
        return errorLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(err -> ErrorLogDto.builder()
                        .id(err.getId())
                        .invoiceId(err.getInvoiceId())
                        .fieldName(err.getFieldName())
                        .originalAiValue(err.getOriginalAiValue())
                        .correctedValue(err.getCorrectedValue())
                        .reason(err.getReason())
                        .correctedBy(err.getCorrectedBy())
                        .createdAt(err.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
