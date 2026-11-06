package com.fsa.expense.dto;

import com.fsa.expense.entity.InvoiceStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewInvoiceRequest {
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private String vendorName;
    private String taxCode;
    private String buyerName;
    private String buyerTaxCode;
    private String buyerAddress;
    private String sellerAddress;
    private BigDecimal subtotalAmount;
    private BigDecimal vatAmount;
    private BigDecimal totalAmount;
    private String category;
    private String debitAccount;
    private String vatAccount;
    private String creditAccount;
    private InvoiceStatus status; // APPROVED or REJECTED
    private String reviewedBy;
    private String reason; // Lý do chỉnh sửa hoặc từ chối
    @Builder.Default
    private List<InvoiceLineItemDto> lineItems = new ArrayList<>();
}
