package com.fsa.expense.service;

import com.fsa.expense.entity.Invoice;
import com.fsa.expense.entity.InvoiceLineItem;
import com.fsa.expense.repository.InvoiceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AuditValidationService {

    private final InvoiceRepository invoiceRepository;
    private static final BigDecimal ROUNDING_TOLERANCE = BigDecimal.valueOf(1000.0);
    private static final BigDecimal CASHLESS_THRESHOLD = BigDecimal.valueOf(20000000.0);
    private static final Pattern TAX_CODE_PATTERN = Pattern.compile("^[0-9]{10}(-[0-9]{3})?$");
    private final NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

    public AuditValidationService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    public static String computeSha256(byte[] data) {
        if (data == null || data.length == 0) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Error computing SHA-256 hash", e);
            return "";
        }
    }

    public void auditInvoice(Invoice invoice, byte[] fileBytes) {
        if (fileBytes != null && fileBytes.length > 0) {
            auditIdempotency(invoice, fileBytes);
        }
        auditArithmetic(invoice);
        auditDuplicate(invoice);
        auditCashlessThreshold(invoice);
        auditTaxCode(invoice);
        auditAccountingMapping(invoice);
    }

    public void auditIdempotency(Invoice invoice, byte[] fileBytes) {
        String hash = computeSha256(fileBytes);
        invoice.setContentHash(hash);

        List<Invoice> duplicates = invoiceRepository.findByContentHashExcluding(hash, invoice.getId());
        if (!duplicates.isEmpty()) {
            Invoice existing = duplicates.get(0);
            invoice.setIsTechnicalDuplicate(true);
            String msg = String.format(
                    "Cảnh báo trùng lặp tệp gốc (SHA-256): Tệp hóa đơn này có nội dung trùng khớp 100%% với Hóa đơn #%d (nộp ngày %s bởi %s). Tránh tạo hóa đơn bóng (ghost invoice).",
                    existing.getId(),
                    existing.getCreatedAt() != null ? existing.getCreatedAt().toLocalDate() : "trước đây",
                    existing.getSubmittedBy() != null ? existing.getSubmittedBy() : "nhân viên khác"
            );
            invoice.setTechnicalDuplicateMessage(msg);
            log.warn("Technical duplicate detected for invoice #{}: {}", invoice.getId(), msg);
        } else {
            invoice.setIsTechnicalDuplicate(false);
            invoice.setTechnicalDuplicateMessage(null);
        }
    }

    public void auditArithmetic(Invoice invoice) {
        BigDecimal totalAmount = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal vatAmount = invoice.getVatAmount() != null ? invoice.getVatAmount() : BigDecimal.ZERO;

        BigDecimal sumLineItems = BigDecimal.ZERO;
        if (invoice.getLineItems() != null && !invoice.getLineItems().isEmpty()) {
            for (InvoiceLineItem item : invoice.getLineItems()) {
                if (item.getAmount() != null) {
                    sumLineItems = sumLineItems.add(item.getAmount());
                }
            }
        } else if (invoice.getSubtotalAmount() != null) {
            sumLineItems = invoice.getSubtotalAmount();
        }

        BigDecimal calculatedTotal = sumLineItems.add(vatAmount);
        BigDecimal diff = calculatedTotal.subtract(totalAmount).abs();

        if (diff.compareTo(ROUNDING_TOLERANCE) <= 0) {
            invoice.setArithmeticValid(true);
            invoice.setArithmeticErrorMessage(null);
            log.info("Invoice #{} passed arithmetic audit (Calculated: {}, Stated: {})",
                    invoice.getInvoiceNumber(), calculatedTotal, totalAmount);
        } else {
            invoice.setArithmeticValid(false);
            String errorMsg = String.format(
                    "Sai lệch số học: Tổng thành tiền mặt hàng (%s) + VAT (%s) = %s, nhưng tổng tiền ghi nhận là %s (chênh lệch %s)",
                    currencyFormatter.format(sumLineItems),
                    currencyFormatter.format(vatAmount),
                    currencyFormatter.format(calculatedTotal),
                    currencyFormatter.format(totalAmount),
                    currencyFormatter.format(diff)
            );
            invoice.setArithmeticErrorMessage(errorMsg);
            log.warn("Invoice #{} failed arithmetic audit: {}", invoice.getInvoiceNumber(), errorMsg);
        }
    }

    public void auditDuplicate(Invoice invoice) {
        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank()
                || invoice.getTaxCode() == null || invoice.getTaxCode().isBlank()) {
            invoice.setDuplicateSuspected(false);
            invoice.setDuplicateWarningMessage(null);
            return;
        }

        List<Invoice> existingDuplicates = invoiceRepository.findPotentialDuplicates(
                invoice.getInvoiceNumber().trim(),
                invoice.getTaxCode().trim(),
                invoice.getId()
        );

        if (!existingDuplicates.isEmpty()) {
            Invoice duplicate = existingDuplicates.get(0);
            invoice.setDuplicateSuspected(true);
            String warningMsg = String.format(
                    "Cảnh báo trùng lặp chứng từ: Hóa đơn số '%s' với Mã số thuế '%s' đã từng được nộp vào hệ thống trước đó (Hóa đơn ID #%d, nộp bởi '%s' ngày %s)",
                    invoice.getInvoiceNumber(),
                    invoice.getTaxCode(),
                    duplicate.getId(),
                    duplicate.getSubmittedBy() != null ? duplicate.getSubmittedBy() : "Nhân viên khác",
                    duplicate.getCreatedAt() != null ? duplicate.getCreatedAt().toLocalDate() : "trước đây"
            );
            invoice.setDuplicateWarningMessage(warningMsg);
            log.warn("Invoice duplicate detected: {}", warningMsg);
        } else {
            invoice.setDuplicateSuspected(false);
            invoice.setDuplicateWarningMessage(null);
        }
    }

    public void auditCashlessThreshold(Invoice invoice) {
        BigDecimal totalAmount = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;
        if (totalAmount.compareTo(CASHLESS_THRESHOLD) >= 0) {
            invoice.setCashlessRequired(true);
            String warningMsg = String.format(
                    "Ngưỡng thanh toán không dùng tiền mặt (CASHLESS_THRESHOLD): Hóa đơn có giá trị %s (>= 20.000.000 VNĐ) bắt buộc phải thanh toán không dùng tiền mặt (chuyển khoản/ủy nhiệm chi) theo Luật thuế GTGT để được khấu trừ thuế và tính chi phí hợp lệ.",
                    currencyFormatter.format(totalAmount)
            );
            invoice.setCashlessWarningMessage(warningMsg);
            log.info("Invoice #{} triggered cashless threshold warning", invoice.getInvoiceNumber());
        } else {
            invoice.setCashlessRequired(false);
            invoice.setCashlessWarningMessage(null);
        }
    }

    public void auditTaxCode(Invoice invoice) {
        if (invoice.getTaxCode() == null || invoice.getTaxCode().isBlank()) {
            invoice.setTaxCodeValid(true);
            invoice.setTaxCodeErrorMessage(null);
            return;
        }

        String cleanTax = invoice.getTaxCode().trim();
        if (TAX_CODE_PATTERN.matcher(cleanTax).matches()) {
            invoice.setTaxCodeValid(true);
            invoice.setTaxCodeErrorMessage(null);
        } else {
            invoice.setTaxCodeValid(false);
            String errorMsg = String.format(
                    "Định dạng Mã số thuế không đúng chuẩn: '%s'. Chuẩn MST Việt Nam gồm 10 số (Doanh nghiệp) hoặc 13 số (Chi nhánh).",
                    cleanTax
            );
            invoice.setTaxCodeErrorMessage(errorMsg);
            log.warn("Invoice #{} has invalid tax code: {}", invoice.getInvoiceNumber(), errorMsg);
        }
    }

    public void auditAccountingMapping(Invoice invoice) {
        String cat = invoice.getCategory() != null ? invoice.getCategory().trim() : "";
        BigDecimal total = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;

        // Tài khoản Nợ
        if (cat.contains("Công tác")) {
            invoice.setDebitAccount("6427"); // Chi phí công tác
        } else if (cat.contains("Ăn uống") || cat.contains("Tiếp khách")) {
            invoice.setDebitAccount("6428"); // Chi phí tiếp khách
        } else if (cat.contains("Vật tư") || cat.contains("Thiết bị")) {
            if (total.compareTo(BigDecimal.valueOf(30000000.0)) >= 0) {
                invoice.setDebitAccount("211"); // Tài sản cố định nếu >= 30M
            } else {
                invoice.setDebitAccount("153"); // Công cụ dụng cụ
            }
        } else if (cat.contains("Đào tạo") || cat.contains("Hội thảo")) {
            invoice.setDebitAccount("6427"); // Chi phí đào tạo
        } else {
            invoice.setDebitAccount("6427"); // Chi phí dịch vụ mua ngoài
        }

        // Thuế GTGT
        invoice.setVatAccount("1331");

        // Tài khoản Có
        if (Boolean.TRUE.equals(invoice.getCashlessRequired())) {
            invoice.setCreditAccount("1121"); // Tiền gửi ngân hàng
        } else {
            invoice.setCreditAccount("1111"); // Tiền mặt
        }
    }
}
