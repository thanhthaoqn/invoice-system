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
    public IntakeController(IntakeService service, IntakeLimits limits, IntakeItemRepository items, IntakeAttemptRepository attempts, FileStorageService storage) {
        this.service=service; this.limits=limits; this.items=items; this.attempts=attempts; this.storage=storage;
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
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("document-"+id+".bin").build().toString())
            .body(storage.loadFileAsResource(item.getStoragePath()));
    }
    private IntakeItem item(Long id) { return items.findByIdAndTenantId(id, TenantContext.id()).orElseThrow(() -> AuthService.error(404, "Không tìm thấy tài liệu")); }
}
