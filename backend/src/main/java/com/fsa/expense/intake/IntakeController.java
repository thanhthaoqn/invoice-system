package com.fsa.expense.intake;

import com.fsa.expense.auth.*;
import com.fsa.expense.service.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController @RequestMapping("/api/intake")
public class IntakeController {
    private final IntakeService service;
    private final IntakeLimits limits;
    private final IntakeItemRepository items;
    private final IntakeAttemptRepository attempts;
    private final FileStorageService storage;
    private final IntakeProcessor processor;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    public IntakeController(IntakeService service, IntakeLimits limits, IntakeItemRepository items, IntakeAttemptRepository attempts, FileStorageService storage,IntakeProcessor processor,com.fasterxml.jackson.databind.ObjectMapper json) {
        this.service=service; this.limits=limits; this.items=items; this.attempts=attempts; this.storage=storage;
        this.processor=processor; this.json=json;
    }
    public record PdfDraft(long version,com.fsa.expense.dto.AiExtractionDto data) {}
    @GetMapping("/items/{id}/draft") public PdfDraft draft(@PathVariable Long id) throws com.fasterxml.jackson.core.JsonProcessingException {
        IntakeItem item=item(id);
        if (!("PDF".equals(item.getDocumentType()) || "IMAGE".equals(item.getDocumentType())) || item.getDraftJson()==null) throw AuthService.error(409,"Chưa có dữ liệu trích xuất tài liệu");
        return new PdfDraft(item.getVersion(),json.readValue(item.getDraftJson(),com.fsa.expense.dto.AiExtractionDto.class));
    }
    @PutMapping("/items/{id}/draft") public PdfDraft saveDraft(@PathVariable Long id,@RequestBody PdfDraft draft) throws com.fasterxml.jackson.core.JsonProcessingException {
        if(draft.data()==null) throw new IllegalArgumentException("Thiếu dữ liệu PDF");
        processor.savePdf(id,draft.version(),draft.data(),false);
        return draft(id);
    }
    @PostMapping("/items/{id}/confirm") public IntakeItem confirm(@PathVariable Long id,@RequestBody PdfDraft draft) {
        if(draft.data()==null) throw new IllegalArgumentException("Thiếu dữ liệu PDF");
        return processor.savePdf(id,draft.version(),draft.data(),true);
    }
    @GetMapping("/limits") public IntakeLimits limits() { return limits; }
    @GetMapping("/sessions") public List<IntakeService.SessionResult> history() { return service.history(); }
    @GetMapping("/sessions/{id}") public IntakeService.SessionResult get(@PathVariable Long id) { return service.get(id); }
    @PostMapping(value="/sessions", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public IntakeService.SessionResult upload(@RequestParam("files") List<MultipartFile> files) { return service.upload(files); }
    @PostMapping("/sessions/{id}/items/{itemId}/retry")
    public IntakeService.SessionResult retry(@PathVariable Long id, @PathVariable Long itemId) { return service.retry(id, itemId); }
    @GetMapping("/items/{id}/attempts") public List<IntakeAttempt> attempts(@PathVariable Long id) {
        item(id); return attempts.findByItemIdAndTenantIdOrderById(id, TenantContext.id());
    }
    @GetMapping("/items/{id}/file") public ResponseEntity<Resource> file(@PathVariable Long id) {
        IntakeItem item=item(id);
        if (item.getStoragePath()==null) throw AuthService.error(404, "Chưa có tài liệu gốc được lưu");
        boolean pdf="PDF".equals(item.getDocumentType()), image="IMAGE".equals(item.getDocumentType());
        MediaType type=pdf ? MediaType.APPLICATION_PDF : image && item.getFileName().toLowerCase().endsWith(".png") ? MediaType.IMAGE_PNG : image ? MediaType.IMAGE_JPEG : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().contentType(type)
            .header("X-Content-Type-Options","nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION, (pdf || image ? ContentDisposition.inline() : ContentDisposition.attachment()).filename("document-"+id+(pdf ? ".pdf" : image ? item.getFileName().toLowerCase().endsWith(".png") ? ".png" : ".jpg" : ".bin")).build().toString())
            .body(storage.loadFileAsResource(item.getStoragePath()));
    }
    private IntakeItem item(Long id) { return items.findByIdAndTenantId(id, TenantContext.id()).orElseThrow(() -> AuthService.error(404, "Không tìm thấy tài liệu")); }
}
