package com.fsa.expense.intake;

import com.fsa.expense.auth.*;
import com.fsa.expense.service.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.util.*;
import java.util.zip.*;

@Service
public class IntakeService {
    private final UploadSessionRepository sessions;
    private final IntakeItemRepository items;
    private final FileStorageService storage;
    private final IntakeProcessor processor;
    private final IntakeLimits limits;
    public IntakeService(UploadSessionRepository sessions, IntakeItemRepository items, FileStorageService storage, IntakeProcessor processor, IntakeLimits limits) {
        this.sessions=sessions; this.items=items; this.storage=storage; this.processor=processor; this.limits=limits;
    }
    public record SessionResult(UploadSession session, List<IntakeItem> items, Map<String, Long> counts) {}
    public List<SessionResult> history() {
        return sessions.findTop100ByTenantIdOrderByCreatedAtDesc(TenantContext.id()).stream().map(this::result).toList();
    }
    public SessionResult get(Long id) { return result(session(id)); }
    public UploadSession session(Long id) {
        return sessions.findByIdAndTenantId(id, TenantContext.id()).orElseThrow(() -> AuthService.error(404, "Không tìm thấy phiên tải lên"));
    }
    public SessionResult upload(List<MultipartFile> files) {
        if (files.isEmpty() || files.size() > limits.getMaxFiles()) throw new IllegalArgumentException("Số file vượt giới hạn hoặc chưa chọn file");
        long total = 0;
        for (var file : files) {
            if (file.isEmpty() || file.getSize() > limits.getMaxFileBytes()) throw new IllegalArgumentException("File trống hoặc vượt giới hạn dung lượng");
            total += file.getSize();
            if (total > limits.getMaxBatchBytes()) throw new IllegalArgumentException("Tổng dung lượng vượt giới hạn lô");
        }
        UploadSession session = new UploadSession(); session.setTenantId(TenantContext.id()); session.setSubmittedBy(TenantContext.username()); session.setUploadedFiles(files.size());
        sessions.save(session);
        Budget budget = new Budget();
        try {
            for (var file : files) {
                String name = safeName(file.getOriginalFilename());
                byte[] bytes;
                try { bytes=file.getBytes(); }
                catch (IOException e) { record(session, name, null, false, "STORAGE_FAILED", "Không đọc được file tải lên"); continue; }
                String lower=name.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".zip")) unpack(session, name, bytes, budget);
                else if (looksLikeXml(bytes) || lower.endsWith(".xml") || lower.endsWith(".pdf")) {
                    record(session, name, bytes, looksLikeXml(bytes) || lower.endsWith(".xml"), "RECEIVED", "Đã lưu bản gốc");
                } else record(session, name, bytes, false, "UNSUPPORTED", "Chỉ nhận XML, PDF hoặc ZIP");
            }
            var documents=items.findBySessionIdAndTenantIdOrderById(session.getId(),session.getTenantId());
            documents.stream().filter(IntakeItem::isXmlDocument).forEach(this::process);
            var xmls=items.findBySessionIdAndTenantIdOrderById(session.getId(),session.getTenantId()).stream()
                .filter(i -> i.isXmlDocument() && i.getInvoiceId()!=null).toList();
            for(var pdf:documents) {
                if(!"PDF".equals(pdf.getDocumentType()) || !"RECEIVED".equals(pdf.getState())) continue;
                var matches=xmls.stream().filter(i -> stem(i.getFileName()).equalsIgnoreCase(stem(pdf.getFileName()))).toList();
                IntakeItem match=matches.size()==1?matches.get(0):xmls.size()==1?xmls.get(0):null;
                if(match!=null) {
                    pdf.setInvoiceId(match.getInvoiceId()); pdf.setState("PDF_ATTACHED");
                    pdf.setMessage("PDF hiển thị kèm hóa đơn XML; không chạy OCR"); items.save(pdf);
                } else process(pdf);
            }
            session.setState("COMPLETED");
        } catch (RuntimeException e) {
            session.setState("NEEDS_ATTENTION");
        }
        sessions.save(session);
        return result(session);
    }
    private void unpack(UploadSession session, String name, byte[] bytes, Budget budget) {
        IntakeItem archive = record(session, name, bytes, false, "ARCHIVE_RECEIVED", "Đã lưu ZIP gốc");
        if ("STORAGE_FAILED".equals(archive.getState())) return;
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K' || !((bytes[2] == 3 && bytes[3] == 4) || (bytes[2] == 5 && bytes[3] == 6))) {
            archive.setState("INVALID_ARCHIVE"); archive.setMessage("Nội dung file không phải ZIP được hỗ trợ"); items.save(archive); return;
        }
        int xmlCount = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry=zip.getNextEntry()) != null) {
                if (++budget.entries > limits.getMaxEntries()) throw new IllegalArgumentException("Vượt giới hạn số entry trong lô");
                String path = entry.getName().replace('\\', '/');
                if (path.startsWith("/") || path.contains(":") || Arrays.asList(path.split("/")).contains("..") || path.length() > 700 || path.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Đường dẫn ZIP không an toàn");
                boolean xml = path.toLowerCase(Locale.ROOT).endsWith(".xml");
                // Drain every entry under a shared budget, including unsupported files.
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192]; int count; long size = 0;
                while ((count=zip.read(buffer)) != -1) {
                    size+=count; budget.expanded+=count;
                    if (budget.expanded > limits.getMaxExpandedBytes() || size > limits.getMaxFileBytes()
                        || size > (long) bytes.length * limits.getMaxCompressionRatio()) throw new IllegalArgumentException("Vượt giới hạn giải nén hoặc tỉ lệ nén");
                    output.write(buffer, 0, count);
                }
                long compressed=entry.getCompressedSize();
                if (size > Math.max(1L, compressed) * limits.getMaxCompressionRatio()) throw new IllegalArgumentException("Entry vượt tỉ lệ nén cho phép");
                if (entry.isDirectory()) continue;
                xml = xml || looksLikeXml(output.toByteArray());
                if (xml || path.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    xmlCount++;
                    record(session, name+"/"+path, output.toByteArray(), xml, "RECEIVED", "Đã lưu tài liệu từ ZIP");
                } else if (path.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    record(session, name+"/"+path, null, false, "UNSUPPORTED", "Không hỗ trợ ZIP lồng nhau; bản gốc nằm trong ZIP đã lưu");
                }
            }
            archive.setState(xmlCount == 0 ? "ARCHIVE_EMPTY" : "ARCHIVE_PROCESSED");
            archive.setMessage(xmlCount == 0 ? "ZIP không chứa XML/PDF phù hợp" : "Đã đọc " + xmlCount + " tài liệu XML/PDF");
        } catch (Exception e) {
            archive.setState("ARCHIVE_FAILED");
            archive.setMessage(e instanceof IllegalArgumentException ? e.getMessage() : "ZIP hỏng hoặc không được hỗ trợ; các tài liệu đã nhận vẫn được giữ");
        }
        items.save(archive);
    }
    private void process(IntakeItem item) {
        if (!"RECEIVED".equals(item.getState())) return;
        try { processor.process(item.getId(), false); }
        catch (RuntimeException e) {
            // The per-document transaction rolled back. Keep the durable source for retry.
            processor.recordFailure(item.getId());
        }
    }
    private IntakeItem record(UploadSession session, String name, byte[] bytes, boolean xml, String state, String message) {
        IntakeItem item = new IntakeItem(); item.setTenantId(session.getTenantId()); item.setSessionId(session.getId()); item.setFileName(name);
        item.setXmlDocument(xml); item.setState(state); item.setMessage(message);
        item.setDocumentType(xml ? "XML" : name.toLowerCase(Locale.ROOT).endsWith(".pdf") ? "PDF" : "OTHER");
        if (bytes != null) {
            item.setFileSize(bytes.length); item.setContentHash(AuditValidationService.computeSha256(bytes));
            try { item.setStoragePath(storage.storeBytes(bytes)); }
            catch (RuntimeException e) { item.setState("STORAGE_FAILED"); item.setMessage("Chưa lưu được tài liệu gốc; vui lòng tải lại sau khi khôi phục kho lưu trữ"); }
        }
        return items.save(item);
    }
    public SessionResult retry(Long sessionId, Long itemId) {
        UploadSession session=session(sessionId);
        IntakeItem item=items.findByIdAndTenantId(itemId, TenantContext.id()).orElseThrow(() -> AuthService.error(404, "Không tìm thấy tài liệu"));
        if (!session.getId().equals(item.getSessionId())) throw AuthService.error(404, "Tài liệu không thuộc phiên");
        try { processor.process(itemId, true); }
        catch (org.springframework.web.server.ResponseStatusException e) { throw e; }
        catch (RuntimeException e) { processor.recordFailure(itemId); }
        return result(session);
    }
    private SessionResult result(UploadSession session) {
        List<IntakeItem> list=items.findBySessionIdAndTenantIdOrderById(session.getId(), TenantContext.id());
        Map<String, Long> counts=new LinkedHashMap<>();
        counts.put("uploadedFiles", (long) session.getUploadedFiles());
        counts.put("xmlDocuments", list.stream().filter(IntakeItem::isXmlDocument).count());
        counts.put("pdfDocuments",list.stream().filter(i -> "PDF".equals(i.getDocumentType())).count());
        counts.put("OCR_REVIEW",list.stream().filter(i -> "OCR_REVIEW".equals(i.getState())).count());
        counts.put("OCR_FAILED",list.stream().filter(i -> "OCR_FAILED".equals(i.getState())).count());
        for (String state : List.of("INGESTED", "ALREADY_RECEIVED", "DUPLICATE_IDENTITY", "PARSE_FAILED", "REJECTED_OUTBOUND", "PROCESSING_FAILED", "STORAGE_FAILED", "ARCHIVE_FAILED", "INVALID_ARCHIVE", "ARCHIVE_EMPTY", "UNSUPPORTED"))
            counts.put(state, list.stream().filter(i -> state.equals(i.getState())).count());
        return new SessionResult(session, list, counts);
    }
    private String safeName(String name) {
        if (name == null) return "unnamed";
        String[] parts=name.replace('\\','/').split("/", -1);
        String value=parts[parts.length-1].replaceAll("[\\p{Cntrl}]", "_");
        if (value.isBlank()) return "unnamed";
        return value.substring(0, Math.min(value.length(), 200));
    }
    private static class Budget { int entries; long expanded; }
    private static String stem(String name) {
        String base=name.replace('\\','/'); base=base.substring(base.lastIndexOf('/')+1);
        int dot=base.lastIndexOf('.'); return dot<0?base:base.substring(0,dot);
    }
    static boolean looksLikeXml(byte[] bytes) {
        int offset=bytes.length>=3 && (bytes[0]&255)==239 && (bytes[1]&255)==187 && (bytes[2]&255)==191?3:0;
        while(offset<bytes.length && (bytes[offset]==' ' || bytes[offset]=='\n' || bytes[offset]=='\r' || bytes[offset]=='\t')) offset++;
        return offset<bytes.length && bytes[offset]=='<';
    }
}
