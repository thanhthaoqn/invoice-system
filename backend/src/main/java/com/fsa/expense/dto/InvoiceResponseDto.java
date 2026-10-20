package com.fsa.expense.dto;

import com.fsa.expense.entity.InvoiceStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceResponseDto {
    private Long id;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private String vendorName;
    private String taxCode;
    private BigDecimal subtotalAmount;
    private BigDecimal vatAmount;
    private BigDecimal totalAmount;
    private String category;
    private String filePath;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String fileUrl;
    private InvoiceStatus status;
    private Boolean arithmeticValid;
    private String arithmeticErrorMessage;
    private Boolean duplicateSuspected;
    private String duplicateWarningMessage;
    private String contentHash;
    private Boolean isTechnicalDuplicate;
    private String technicalDuplicateMessage;
    private Boolean cashlessRequired;
    private String cashlessWarningMessage;
    private String debitAccount;
    private String vatAccount;
    private String creditAccount;
    private String extractionMethod;
    private Boolean taxCodeValid;
    private String taxCodeErrorMessage;
    private String rawAiResponse;
    private String submittedBy;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Builder.Default
    private List<InvoiceLineItemDto> lineItems = new ArrayList<>();
}
