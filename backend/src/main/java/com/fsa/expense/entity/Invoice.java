package com.fsa.expense.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", length = 50)
    private String invoiceNumber;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "vendor_name", length = 255)
    private String vendorName;

    @Column(name = "tax_code", length = 50)
    private String taxCode;

    @Column(name = "subtotal_amount", precision = 15, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(name = "vat_amount", precision = 15, scale = 2)
    private BigDecimal vatAmount;

    @Column(name = "total_amount", precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(length = 100)
    private String category;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_type", nullable = false, length = 50)
    private String fileType; // IMAGE or PDF

    @Column(name = "file_size")
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private InvoiceStatus status;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "is_technical_duplicate")
    @Builder.Default
    private Boolean isTechnicalDuplicate = false;

    @Column(name = "technical_duplicate_message", columnDefinition = "TEXT")
    private String technicalDuplicateMessage;

    @Column(name = "arithmetic_valid")
    @Builder.Default
    private Boolean arithmeticValid = true;

    @Column(name = "arithmetic_error_message", columnDefinition = "TEXT")
    private String arithmeticErrorMessage;

    @Column(name = "duplicate_suspected")
    @Builder.Default
    private Boolean duplicateSuspected = false;

    @Column(name = "duplicate_warning_message", columnDefinition = "TEXT")
    private String duplicateWarningMessage;

    @Column(name = "cashless_required")
    @Builder.Default
    private Boolean cashlessRequired = false;

    @Column(name = "cashless_warning_message", columnDefinition = "TEXT")
    private String cashlessWarningMessage;

    @Column(name = "debit_account", length = 50)
    private String debitAccount;

    @Column(name = "vat_account", length = 50)
    private String vatAccount;

    @Column(name = "credit_account", length = 50)
    private String creditAccount;

    @Column(name = "extraction_method", length = 50)
    private String extractionMethod;

    @Column(name = "tax_code_valid")
    @Builder.Default
    private Boolean taxCodeValid = true;

    @Column(name = "tax_code_error_message", columnDefinition = "TEXT")
    private String taxCodeErrorMessage;

    @Column(name = "raw_ai_response", columnDefinition = "TEXT")
    private String rawAiResponse;

    @Column(name = "submitted_by", length = 100)
    @Builder.Default
    private String submittedBy = "Nhân viên Nguyễn Văn A";

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    @Builder.Default
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = InvoiceStatus.PENDING_REVIEW;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
