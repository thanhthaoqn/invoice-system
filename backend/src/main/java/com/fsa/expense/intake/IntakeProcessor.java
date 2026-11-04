package com.fsa.expense.intake;

import com.fsa.expense.auth.*;
import com.fsa.expense.entity.Invoice;
import com.fsa.expense.repository.InvoiceRepository;
import com.fsa.expense.service.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.file.Files;
import java.time.Instant;

@Service
public class IntakeProcessor {
    private final IntakeItemRepository items;
    private final IntakeAttemptRepository attempts;
    private final TenantRepository tenants;
    private final InvoiceRepository invoices;
    private final InvoiceXmlParser parser;
    private final FileStorageService storage;
    private final AuditValidationService audit;
    private final IntakeLimits limits;
    public IntakeProcessor(IntakeItemRepository items, IntakeAttemptRepository attempts, TenantRepository tenants,
            InvoiceRepository invoices, InvoiceXmlParser parser, FileStorageService storage, AuditValidationService audit, IntakeLimits limits) {
        this.items=items; this.attempts=attempts; this.tenants=tenants; this.invoices=invoices;
        this.parser=parser; this.storage=storage; this.audit=audit; this.limits=limits;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public IntakeItem process(Long id, boolean retry) {
        // Serialize canonical creation and retry per tenant across application instances.
        Tenant tenant = tenants.lockById(TenantContext.id()).orElseThrow();
        IntakeItem item = items.findByIdAndTenantId(id, tenant.getId()).orElseThrow(() -> AuthService.error(404, "Không tìm thấy tài liệu"));
        boolean stale = "RECEIVED".equals(item.getState()) && item.getUpdatedAt().isBefore(Instant.now().minusSeconds(300));
        if (retry && !("PARSE_FAILED".equals(item.getState()) || "PROCESSING_FAILED".equals(item.getState()) || stale)) throw AuthService.error(409, "Tài liệu không thuộc trạng thái được xử lý lại hoặc vẫn đang xử lý");
        if (!retry && !"RECEIVED".equals(item.getState())) return item;
        if (item.getAttempts() >= limits.getMaxAttempts()) throw AuthService.error(409, "Đã đạt giới hạn xử lý lại");
        item.setAttempts(item.getAttempts()+1);
        byte[] bytes;
        try { bytes = Files.readAllBytes(storage.getFilePath(item.getStoragePath())); }
        catch (Exception e) { return finish(item, "PROCESSING_FAILED", "Không đọc được bản gốc; kiểm tra kho lưu trữ"); }
        var same = items.findFirstByTenantIdAndContentHashAndStateOrderById(tenant.getId(), item.getContentHash(), "INGESTED");
        if (same.isPresent()) {
            item.setOriginalItemId(same.get().getId()); item.setInvoiceId(same.get().getInvoiceId());
            return finish(item, "ALREADY_RECEIVED", "Đã tiếp nhận nội dung này trước đó");
        }
        Invoice invoice;
        try { invoice=parser.parse(bytes); }
        catch (IllegalArgumentException e) { return finish(item, "PARSE_FAILED", e.getMessage()); }
        if (tenant.getTaxCode().equals(invoice.getTaxCode())) return finish(item, "REJECTED_OUTBOUND", "Đây là hóa đơn đầu ra của doanh nghiệp");
        var duplicate = invoices.findByTenantIdAndIdentityKey(tenant.getId(), invoice.getIdentityKey());
        if (duplicate.isPresent()) {
            item.setInvoiceId(duplicate.get().getId());
            return finish(item, "DUPLICATE_IDENTITY", "Khác nội dung nhưng trùng MST bán, mẫu số, ký hiệu và số hóa đơn");
        }
        invoice.setTenantId(tenant.getId()); invoice.setContentHash(item.getContentHash());
        invoice.setFilePath(item.getStoragePath()); invoice.setFileName(baseName(item.getFileName()));
        invoice.setFileSize(item.getFileSize()); invoice.setSubmittedBy(TenantContext.username());
        String message = "Đã tạo hóa đơn; chờ đối soát, chưa phê duyệt. Chưa kiểm tra chữ ký XML.";
        try {
            audit.auditInvoice(invoice, bytes);
            invoice.setValidationState("CHECKED");
            if (!tenant.getTaxCode().equals(invoice.getBuyerTaxCode())) {
                invoice.setValidationState("BUYER_MISMATCH");
                message += " MST người mua thiếu hoặc khác doanh nghiệp nhận.";
            }
        } catch (RuntimeException e) {
            invoice.setValidationState("NOT_CHECKED"); invoice.setArithmeticValid(null);
            message = "Đã tạo hóa đơn nhưng chưa kiểm tra; cần chạy lại kiểm tra.";
        }
        item.setInvoiceId(invoices.saveAndFlush(invoice).getId());
        return finish(item, "INGESTED", message);
    }
    private IntakeItem finish(IntakeItem item, String state, String message) {
        item.setState(state); item.setMessage(message); item.setUpdatedAt(Instant.now());
        IntakeAttempt attempt=new IntakeAttempt(); attempt.setTenantId(item.getTenantId()); attempt.setItemId(item.getId()); attempt.setState(state); attempt.setMessage(message);
        attempts.save(attempt);
        return items.save(item);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void recordFailure(Long id) {
        tenants.lockById(TenantContext.id()).orElseThrow();
        IntakeItem item=items.findByIdAndTenantId(id,TenantContext.id()).orElseThrow();
        if (!item.isRetryable() && !"RECEIVED".equals(item.getState())) return;
        if (item.getAttempts() >= limits.getMaxAttempts()) throw AuthService.error(409, "Đã đạt giới hạn xử lý lại");
        item.setAttempts(item.getAttempts()+1);
        finish(item,"PROCESSING_FAILED","Xử lý chưa hoàn tất; có thể xử lý lại từ bản gốc");
    }
    private String baseName(String name) { String[] parts=name.replace('\\','/').split("/"); return parts[parts.length-1]; }
}
