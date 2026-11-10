package com.fsa.expense.intake;

import com.fsa.expense.auth.*;
import com.fsa.expense.entity.Invoice;
import com.fsa.expense.repository.InvoiceRepository;
import com.fsa.expense.service.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Optional;

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
    private final AiExtractionClient extraction;
    private final PdfInvoiceMapper pdfMapper;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    private final com.fsa.expense.email.TenantAliasRepository aliases;
    private final com.fsa.expense.email.EmailMessageRepository emails;
    public IntakeProcessor(IntakeItemRepository items, IntakeAttemptRepository attempts, TenantRepository tenants,
            InvoiceRepository invoices, InvoiceXmlParser parser, FileStorageService storage, AuditValidationService audit, IntakeLimits limits,
            AiExtractionClient extraction, PdfInvoiceMapper pdfMapper, com.fasterxml.jackson.databind.ObjectMapper json,
            com.fsa.expense.email.TenantAliasRepository aliases,com.fsa.expense.email.EmailMessageRepository emails) {
        this.items=items; this.attempts=attempts; this.tenants=tenants; this.invoices=invoices;
        this.parser=parser; this.storage=storage; this.audit=audit; this.limits=limits;
        this.extraction=extraction; this.pdfMapper=pdfMapper; this.json=json;
        this.aliases=aliases;
        this.emails=emails;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public IntakeItem process(Long id, boolean retry) {
        // Serialize canonical creation and retry per tenant across application instances.
        Tenant tenant = tenants.lockById(TenantContext.id()).orElseThrow();
        IntakeItem item = items.findByIdAndTenantId(id, tenant.getId()).orElseThrow(() -> AuthService.error(404, "Không tìm thấy tài liệu"));
        boolean stale = "RECEIVED".equals(item.getState()) && item.getUpdatedAt().isBefore(Instant.now().minusSeconds(300));
        if (retry && !("PARSE_FAILED".equals(item.getState()) || "PROCESSING_FAILED".equals(item.getState()) || "OCR_FAILED".equals(item.getState()) || stale)) throw AuthService.error(409, "Tài liệu không thuộc trạng thái được xử lý lại hoặc vẫn đang xử lý");
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
        if ("PDF".equals(item.getDocumentType())) {
            var existing=items.findFirstByTenantIdAndContentHashAndStateOrderById(tenant.getId(), item.getContentHash(), "OCR_REVIEW");
            if (existing.isPresent() && !existing.get().getId().equals(item.getId())) {
                item.setOriginalItemId(existing.get().getId());
                return finish(item,"ALREADY_RECEIVED","PDF đã có hồ sơ chờ xác nhận; mở hồ sơ gốc để tiếp tục");
            }
            if (bytes.length<5 || !new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))
                return finish(item,"OCR_FAILED","Nội dung file không phải PDF");
            try {
                var data=extraction.extractInvoice(new StoredPdf(bytes,baseName(item.getFileName())));
                item.setRawExtraction(json.writeValueAsString(data)); item.setDraftJson(item.getRawExtraction());

                // Chặn trùng danh tính ngay tại bước trích xuất nếu hóa đơn đã được tiếp nhận (từ XML hoặc PDF trước)
                if (data.getTaxCode() != null && data.getInvoiceNumber() != null && data.getInvoiceSeries() != null) {
                    String template = data.getInvoiceTemplate() != null ? data.getInvoiceTemplate() : (data.getInvoiceSeries().length() > 0 ? data.getInvoiceSeries().substring(0, 1) : "1");
                    var duplicate = findDuplicate(tenant.getId(), data.getTaxCode(), template,
                        data.getInvoiceSeries(), data.getInvoiceNumber());
                    if (duplicate.isPresent()) {
                        item.setInvoiceId(duplicate.get().getId());
                        return finish(item, "DUPLICATE_IDENTITY", "Hóa đơn này đã được tiếp nhận trong hệ thống (" + duplicate.get().getFileType() + " #" + duplicate.get().getId() + "); không tạo bản trùng.");
                    }
                }

                return finish(item,"OCR_REVIEW","Đã trích xuất PDF. Kiểm tra, bổ sung trường thiếu và xác nhận để tạo hóa đơn.");
            } catch (IllegalArgumentException | IllegalStateException e) {
                return finish(item,"OCR_FAILED",e.getMessage());
            } catch (Exception e) {
                return finish(item,"OCR_FAILED","Chưa trích xuất được PDF; kiểm tra dịch vụ OCR, mật khẩu hoặc giới hạn trang và thử lại");
            }
        }
        Invoice invoice;
        try { invoice=parser.parse(bytes); }
        catch (IllegalArgumentException e) { return finish(item, "PARSE_FAILED", e.getMessage()); }
        return persist(item,tenant,invoice);
    }
    private IntakeItem persist(IntakeItem item,Tenant tenant,Invoice invoice) {
        if (tenant.getTaxCode().equals(invoice.getTaxCode())) return finish(item, "REJECTED_OUTBOUND", "Đây là hóa đơn đầu ra của doanh nghiệp");
        var request=((org.springframework.web.context.request.ServletRequestAttributes)org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()).getRequest();
        if((Boolean.TRUE.equals(request.getAttribute("emailIntake")) || emails.existsByTenantIdAndSessionId(tenant.getId(),item.getSessionId())) && !tenant.getTaxCode().equals(invoice.getBuyerTaxCode())
                && !aliases.existsByTenantIdAndTaxCode(tenant.getId(),invoice.getBuyerTaxCode()))
            return finish(item,"REJECTED_ROUTING","MST người mua không thuộc doanh nghiệp hoặc chi nhánh phụ thuộc");
        var duplicate = findDuplicate(tenant.getId(), invoice.getTaxCode(), invoice.getInvoiceTemplate(),
            invoice.getInvoiceSeries(), invoice.getInvoiceNumber());
        if (duplicate.isPresent()) {
            item.setInvoiceId(duplicate.get().getId());
            return finish(item, "DUPLICATE_IDENTITY", "Khác nội dung nhưng trùng MST bán, mẫu số, ký hiệu và số hóa đơn");
        }
        invoice.setTenantId(tenant.getId()); invoice.setContentHash(item.getContentHash());
        invoice.setFilePath(item.getStoragePath()); invoice.setFileName(baseName(item.getFileName()));
        invoice.setFileSize(item.getFileSize()); invoice.setSubmittedBy(TenantContext.username());
        String message = "Đã tạo hóa đơn; chờ đối soát, chưa phê duyệt. Chưa xác minh chữ ký điện tử.";
        try {
            audit.auditInvoice(invoice, null);
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
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public IntakeItem savePdf(Long id,long version,com.fsa.expense.dto.AiExtractionDto draft,boolean confirm) {
        Tenant tenant=tenants.lockById(TenantContext.id()).orElseThrow();
        IntakeItem item=items.findByIdAndTenantId(id,tenant.getId()).orElseThrow(() -> AuthService.error(404,"Không tìm thấy hồ sơ PDF"));
        if (!"PDF".equals(item.getDocumentType())) throw AuthService.error(409,"Tài liệu không phải PDF");
        if (confirm && item.getInvoiceId()!=null) return item;
        if (!"OCR_REVIEW".equals(item.getState())) throw AuthService.error(409,"Hồ sơ không còn ở trạng thái chờ xác nhận");
        if (item.getVersion()!=version) throw AuthService.error(409,"Dữ liệu đã thay đổi; mở lại hồ sơ trước khi lưu");
        try {
            // Engine metadata cannot be overwritten by the editable draft.
            var original=json.readValue(item.getRawExtraction(),com.fsa.expense.dto.AiExtractionDto.class);
            draft.setExtractionMethod(original.getExtractionMethod());
            item.setDraftJson(json.writeValueAsString(draft));
            if (!confirm) { item.setUpdatedAt(Instant.now()); return items.saveAndFlush(item); }
            Invoice invoice=pdfMapper.invoice(draft);
            invoice.setRawAiResponse(item.getRawExtraction());
            item.setConfirmedBy(TenantContext.username()); item.setConfirmedAt(Instant.now()); item.setConfirmedSnapshot(item.getDraftJson());
            return persist(item,tenant,invoice);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalArgumentException("Dữ liệu PDF không hợp lệ"); }
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public IntakeItem reExtractPdf(Long id) {
        Tenant tenant = tenants.lockById(TenantContext.id()).orElseThrow();
        IntakeItem item = items.findByIdAndTenantId(id, tenant.getId()).orElseThrow(() -> AuthService.error(404, "Không tìm thấy hồ sơ PDF"));
        if (!"PDF".equals(item.getDocumentType())) throw AuthService.error(409, "Tài liệu không phải PDF");
        try {
            byte[] bytes = Files.readAllBytes(storage.getFilePath(item.getStoragePath()));
            var data = extraction.extractInvoice(new StoredPdf(bytes, baseName(item.getFileName())));
            item.setRawExtraction(json.writeValueAsString(data));
            item.setDraftJson(item.getRawExtraction());

            // Chặn trùng danh tính nếu hóa đơn đã được nạp
            if (data.getTaxCode() != null && data.getInvoiceNumber() != null && data.getInvoiceSeries() != null) {
                String template = data.getInvoiceTemplate() != null ? data.getInvoiceTemplate() : (data.getInvoiceSeries().length() > 0 ? data.getInvoiceSeries().substring(0, 1) : "1");
                var duplicate = findDuplicate(tenant.getId(), data.getTaxCode(), template,
                    data.getInvoiceSeries(), data.getInvoiceNumber());
                if (duplicate.isPresent()) {
                    item.setInvoiceId(duplicate.get().getId());
                    return finish(item, "DUPLICATE_IDENTITY", "Hóa đơn này đã được tiếp nhận trong hệ thống (" + duplicate.get().getFileType() + " #" + duplicate.get().getId() + "); không tạo bản trùng.");
                }
            }

            return finish(item, "OCR_REVIEW", "Đã trích xuất lại từ bản gốc PDF.");
        } catch (Exception e) {
            throw AuthService.error(500, "Lỗi khi trích xuất lại: " + e.getMessage());
        }
    }
    private record StoredPdf(byte[] bytes,String filename) implements org.springframework.web.multipart.MultipartFile {
        public String getName(){return "file";} public String getOriginalFilename(){return filename;}
        public String getContentType(){return "application/pdf";} public boolean isEmpty(){return bytes.length==0;}
        public long getSize(){return bytes.length;} public byte[] getBytes(){return bytes;}
        public java.io.InputStream getInputStream(){return new java.io.ByteArrayInputStream(bytes);}
        public void transferTo(java.io.File file) throws java.io.IOException {Files.write(file.toPath(),bytes);}
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
    private Optional<Invoice> findDuplicate(Long tenantId, String taxCode, String template, String series, String number) {
        String identityKey = InvoiceIdentity.hash(taxCode, template, series, number);
        Optional<Invoice> keyed = invoices.findByTenantIdAndIdentityKey(tenantId, identityKey);
        if (keyed.isPresent()) return keyed;
        // Compatibility for rows created before canonical identity keys were introduced.
        String cleanTaxCode = taxCode == null ? "" : taxCode.trim();
        return invoices.findByTenantIdAndTaxCode(tenantId, cleanTaxCode).stream()
            .filter(existing -> InvoiceIdentity.matches(existing, taxCode, template, series, number))
            .findFirst();
    }
}
