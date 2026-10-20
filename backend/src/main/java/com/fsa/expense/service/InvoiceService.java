package com.fsa.expense.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsa.expense.dto.*;
import com.fsa.expense.entity.*;
import com.fsa.expense.repository.ErrorLogRepository;
import com.fsa.expense.repository.InvoiceLineItemRepository;
import com.fsa.expense.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineItemRepository lineItemRepository;
    private final ErrorLogRepository errorLogRepository;
    private final FileStorageService fileStorageService;
    private final AiExtractionClient aiExtractionClient;
    private final AuditValidationService auditValidationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InvoiceService(InvoiceRepository invoiceRepository,
                          InvoiceLineItemRepository lineItemRepository,
                          ErrorLogRepository errorLogRepository,
                          FileStorageService fileStorageService,
                          AiExtractionClient aiExtractionClient,
                          AuditValidationService auditValidationService) {
        this.invoiceRepository = invoiceRepository;
        this.lineItemRepository = lineItemRepository;
        this.errorLogRepository = errorLogRepository;
        this.fileStorageService = fileStorageService;
        this.aiExtractionClient = aiExtractionClient;
        this.auditValidationService = auditValidationService;
    }

    @Transactional
    public InvoiceResponseDto uploadAndProcess(MultipartFile file, String submittedBy) {
        String storedFilename = fileStorageService.storeFile(file);
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "invoice.jpg";
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        String fileType = contentType.toLowerCase().contains("pdf") ? "PDF" : "IMAGE";

        // Call AI Extraction Microservice
        AiExtractionDto aiResult = aiExtractionClient.extractInvoice(file);

        String rawAiJson = "";
        try {
            rawAiJson = objectMapper.writeValueAsString(aiResult);
        } catch (Exception e) {
            log.error("Could not serialize raw AI response", e);
        }

        LocalDate invoiceDate = null;
        if (aiResult.getInvoiceDate() != null && !aiResult.getInvoiceDate().isBlank()) {
            try {
                invoiceDate = LocalDate.parse(aiResult.getInvoiceDate().trim());
            } catch (Exception e) {
                log.warn("Could not parse date string: {}", aiResult.getInvoiceDate());
            }
        }

        byte[] fileBytes = null;
        try {
            fileBytes = file.getBytes();
        } catch (Exception e) {
            log.warn("Could not read file bytes for hashing", e);
        }

        String method = aiResult.getExtractionMethod();
        if (method == null || method.isBlank()) {
            method = fileType.equals("PDF") ? "PYMUPDF_VECTOR_TEXT" : "VLM_GEMINI_VISION";
        }

        Invoice invoice = Invoice.builder()
                .invoiceNumber(aiResult.getInvoiceNumber())
                .invoiceDate(invoiceDate)
                .vendorName(aiResult.getVendorName())
                .taxCode(aiResult.getTaxCode())
                .subtotalAmount(aiResult.getSubtotalAmount())
                .vatAmount(aiResult.getVatAmount())
                .totalAmount(aiResult.getTotalAmount())
                .category(aiResult.getCategory() != null ? aiResult.getCategory() : "Chi phí Dịch vụ / Khác")
                .filePath(storedFilename)
                .fileName(originalFilename)
                .fileType(fileType)
                .fileSize(file.getSize())
                .status(InvoiceStatus.PENDING_REVIEW)
                .rawAiResponse(rawAiJson)
                .submittedBy(submittedBy != null && !submittedBy.isBlank() ? submittedBy : "Nhân viên Nguyễn Văn A")
                .notes(aiResult.getNotes())
                .extractionMethod(method)
                .debitAccount(aiResult.getDebitAccount())
                .vatAccount(aiResult.getVatAccount() != null ? aiResult.getVatAccount() : "1331")
                .creditAccount(aiResult.getCreditAccount())
                .cashlessRequired(aiResult.getCashlessRequired())
                .build();

        // Convert Line Items
        List<InvoiceLineItem> lineItems = new ArrayList<>();
        if (aiResult.getLineItems() != null) {
            int lineNum = 1;
            for (AiExtractionDto.AiLineItemDto itemDto : aiResult.getLineItems()) {
                InvoiceLineItem lineItem = InvoiceLineItem.builder()
                        .invoice(invoice)
                        .itemName(itemDto.getItemName() != null ? itemDto.getItemName() : "Hàng hóa/dịch vụ")
                        .quantity(itemDto.getQuantity() != null ? itemDto.getQuantity() : BigDecimal.ONE)
                        .unitPrice(itemDto.getUnitPrice() != null ? itemDto.getUnitPrice() : BigDecimal.ZERO)
                        .amount(itemDto.getAmount() != null ? itemDto.getAmount() : BigDecimal.ZERO)
                        .vatRate(itemDto.getVatRate() != null ? itemDto.getVatRate() : "10%")
                        .lineNumber(lineNum++)
                        .build();
                lineItems.add(lineItem);
            }
        }
        invoice.setLineItems(lineItems);

        // Run automated compliance audit checks (Idempotency SHA-256, Arithmetic, Duplicate, Cashless Threshold, Tax Code, Account Mapping)
        auditValidationService.auditInvoice(invoice, fileBytes);

        Invoice saved = invoiceRepository.save(invoice);
        log.info("Saved new invoice ID #{} with status {} (method: {})", saved.getId(), saved.getStatus(), saved.getExtractionMethod());
        return toDto(saved);
    }

    public List<InvoiceResponseDto> getAllInvoices(InvoiceStatus status) {
        List<Invoice> list = status == null ?
                invoiceRepository.findAllByOrderByCreatedAtDesc() :
                invoiceRepository.findByStatusOrderByCreatedAtDesc(status);
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    public InvoiceResponseDto getInvoiceById(Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found with id: " + id));
        return toDto(invoice);
    }

    @Transactional
    public InvoiceResponseDto reviewInvoice(Long id, ReviewInvoiceRequest request) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found with id: " + id));

        // Parse original AI extraction to track discrepancies
        AiExtractionDto originalAi = null;
        if (invoice.getRawAiResponse() != null && !invoice.getRawAiResponse().isBlank()) {
            try {
                originalAi = objectMapper.readValue(invoice.getRawAiResponse(), AiExtractionDto.class);
            } catch (Exception e) {
                log.warn("Failed to parse rawAiResponse for invoice #{}", id);
            }
        }

        String reviewer = request.getReviewedBy() != null ? request.getReviewedBy() : "Kế toán viên";
        String reason = request.getReason() != null ? request.getReason() : "Chỉnh sửa đối soát Human-In-The-Loop";

        // Log field-level discrepancies if original AI values differ from human input
        if (originalAi != null) {
            checkAndLogError(invoice.getId(), "invoiceNumber", originalAi.getInvoiceNumber(), request.getInvoiceNumber(), reason, reviewer);
            checkAndLogError(invoice.getId(), "taxCode", originalAi.getTaxCode(), request.getTaxCode(), reason, reviewer);
            checkAndLogError(invoice.getId(), "vendorName", originalAi.getVendorName(), request.getVendorName(), reason, reviewer);
            checkAndLogError(invoice.getId(), "category", originalAi.getCategory(), request.getCategory(), reason, reviewer);
            checkAndLogError(invoice.getId(), "totalAmount",
                    originalAi.getTotalAmount() != null ? originalAi.getTotalAmount().toString() : null,
                    request.getTotalAmount() != null ? request.getTotalAmount().toString() : null,
                    reason, reviewer);
            checkAndLogError(invoice.getId(), "vatAmount",
                    originalAi.getVatAmount() != null ? originalAi.getVatAmount().toString() : null,
                    request.getVatAmount() != null ? request.getVatAmount().toString() : null,
                    reason, reviewer);
        }

        // Apply human updates
        if (request.getInvoiceNumber() != null) invoice.setInvoiceNumber(request.getInvoiceNumber());
        if (request.getInvoiceDate() != null) invoice.setInvoiceDate(request.getInvoiceDate());
        if (request.getVendorName() != null) invoice.setVendorName(request.getVendorName());
        if (request.getTaxCode() != null) invoice.setTaxCode(request.getTaxCode());
        if (request.getSubtotalAmount() != null) invoice.setSubtotalAmount(request.getSubtotalAmount());
        if (request.getVatAmount() != null) invoice.setVatAmount(request.getVatAmount());
        if (request.getTotalAmount() != null) invoice.setTotalAmount(request.getTotalAmount());
        if (request.getCategory() != null) invoice.setCategory(request.getCategory());

        // Update line items if provided
        if (request.getLineItems() != null && !request.getLineItems().isEmpty()) {
            invoice.getLineItems().clear();
            int lineNum = 1;
            for (InvoiceLineItemDto itemDto : request.getLineItems()) {
                InvoiceLineItem item = InvoiceLineItem.builder()
                        .invoice(invoice)
                        .itemName(itemDto.getItemName())
                        .quantity(itemDto.getQuantity() != null ? itemDto.getQuantity() : BigDecimal.ONE)
                        .unitPrice(itemDto.getUnitPrice() != null ? itemDto.getUnitPrice() : BigDecimal.ZERO)
                        .amount(itemDto.getAmount() != null ? itemDto.getAmount() : BigDecimal.ZERO)
                        .vatRate(itemDto.getVatRate() != null ? itemDto.getVatRate() : "10%")
                        .lineNumber(lineNum++)
                        .build();
                invoice.getLineItems().add(item);
            }
        }

        // Re-audit arithmetic, duplicate, cashless threshold, tax code, and accounting mapping
        auditValidationService.auditInvoice(invoice, null);

        // Update status and audit reviewer info
        InvoiceStatus targetStatus = request.getStatus() != null ? request.getStatus() : InvoiceStatus.APPROVED;
        invoice.setStatus(targetStatus);
        invoice.setReviewedBy(reviewer);
        invoice.setReviewedAt(LocalDateTime.now());
        if (request.getReason() != null && !request.getReason().isBlank()) {
            invoice.setNotes(request.getReason());
        }

        Invoice updated = invoiceRepository.save(invoice);
        log.info("Invoice ID #{} reviewed and updated to status {}", updated.getId(), updated.getStatus());
        return toDto(updated);
    }

    private void checkAndLogError(Long invoiceId, String fieldName, String originalVal, String newVal, String reason, String reviewer) {
        String cleanOriginal = originalVal != null ? originalVal.trim() : "";
        String cleanNew = newVal != null ? newVal.trim() : "";

        // If numeric field, compare numerically
        if (fieldName.toLowerCase().contains("amount")) {
            try {
                double oVal = Double.parseDouble(cleanOriginal);
                double nVal = Double.parseDouble(cleanNew);
                if (Math.abs(oVal - nVal) < 0.01) {
                    return; // Same numerical value, skip logging
                }
            } catch (Exception ignored) {
            }
        }

        if (!cleanOriginal.equalsIgnoreCase(cleanNew) && (!cleanOriginal.isEmpty() || !cleanNew.isEmpty())) {
            ErrorLog errorLog = ErrorLog.builder()
                    .invoiceId(invoiceId)
                    .fieldName(fieldName)
                    .originalAiValue(originalVal)
                    .correctedValue(newVal)
                    .reason(reason)
                    .correctedBy(reviewer)
                    .build();
            errorLogRepository.save(errorLog);
            log.info("Logged AI discrepancy for Invoice #{}, field '{}': '{}' -> '{}'",
                    invoiceId, fieldName, originalVal, newVal);
        }
    }

    public InvoiceResponseDto toDto(Invoice invoice) {
        List<InvoiceLineItemDto> itemDtos = new ArrayList<>();
        if (invoice.getLineItems() != null) {
            for (InvoiceLineItem item : invoice.getLineItems()) {
                itemDtos.add(InvoiceLineItemDto.builder()
                        .id(item.getId())
                        .itemName(item.getItemName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .amount(item.getAmount())
                        .vatRate(item.getVatRate())
                        .lineNumber(item.getLineNumber())
                        .build());
            }
        }

        String fileUrl = "/api/invoices/" + invoice.getId() + "/file";

        return InvoiceResponseDto.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .invoiceDate(invoice.getInvoiceDate())
                .vendorName(invoice.getVendorName())
                .taxCode(invoice.getTaxCode())
                .subtotalAmount(invoice.getSubtotalAmount())
                .vatAmount(invoice.getVatAmount())
                .totalAmount(invoice.getTotalAmount())
                .category(invoice.getCategory())
                .filePath(invoice.getFilePath())
                .fileName(invoice.getFileName())
                .fileType(invoice.getFileType())
                .fileSize(invoice.getFileSize())
                .fileUrl(fileUrl)
                .status(invoice.getStatus())
                .arithmeticValid(invoice.getArithmeticValid())
                .arithmeticErrorMessage(invoice.getArithmeticErrorMessage())
                .duplicateSuspected(invoice.getDuplicateSuspected())
                .duplicateWarningMessage(invoice.getDuplicateWarningMessage())
                .contentHash(invoice.getContentHash())
                .isTechnicalDuplicate(invoice.getIsTechnicalDuplicate())
                .technicalDuplicateMessage(invoice.getTechnicalDuplicateMessage())
                .cashlessRequired(invoice.getCashlessRequired())
                .cashlessWarningMessage(invoice.getCashlessWarningMessage())
                .debitAccount(invoice.getDebitAccount())
                .vatAccount(invoice.getVatAccount())
                .creditAccount(invoice.getCreditAccount())
                .extractionMethod(invoice.getExtractionMethod())
                .taxCodeValid(invoice.getTaxCodeValid())
                .taxCodeErrorMessage(invoice.getTaxCodeErrorMessage())
                .rawAiResponse(invoice.getRawAiResponse())
                .submittedBy(invoice.getSubmittedBy())
                .reviewedBy(invoice.getReviewedBy())
                .reviewedAt(invoice.getReviewedAt())
                .notes(invoice.getNotes())
                .createdAt(invoice.getCreatedAt())
                .updatedAt(invoice.getUpdatedAt())
                .lineItems(itemDtos)
                .build();
    }
}
