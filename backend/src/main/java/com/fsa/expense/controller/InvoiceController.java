package com.fsa.expense.controller;

import com.fsa.expense.dto.InvoiceResponseDto;
import com.fsa.expense.dto.ReviewInvoiceRequest;
import com.fsa.expense.entity.Invoice;
import com.fsa.expense.entity.InvoiceStatus;
import com.fsa.expense.service.ExcelExportService;
import com.fsa.expense.service.FileStorageService;
import com.fsa.expense.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@Slf4j
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final FileStorageService fileStorageService;
    private final ExcelExportService excelExportService;

    public InvoiceController(InvoiceService invoiceService,
                             FileStorageService fileStorageService,
                             ExcelExportService excelExportService) {
        this.invoiceService = invoiceService;
        this.fileStorageService = fileStorageService;
        this.excelExportService = excelExportService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InvoiceResponseDto> uploadInvoice(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "submittedBy", required = false) String submittedBy) {
        log.info("Received invoice upload request: {}", file.getOriginalFilename());
        InvoiceResponseDto response = invoiceService.uploadAndProcess(file, submittedBy);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<InvoiceResponseDto>> getInvoices(
            @RequestParam(value = "status", required = false) InvoiceStatus status) {
        return ResponseEntity.ok(invoiceService.getAllInvoices(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponseDto> getInvoice(@PathVariable("id") Long id) {
        return ResponseEntity.ok(invoiceService.getInvoiceById(id));
    }

    @PutMapping("/{id}/review")
    public ResponseEntity<InvoiceResponseDto> reviewInvoice(
            @PathVariable("id") Long id,
            @RequestBody ReviewInvoiceRequest request) {
        log.info("Reviewing invoice #{} with status {}", id, request.getStatus());
        InvoiceResponseDto response = invoiceService.reviewInvoice(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> getInvoiceFile(@PathVariable("id") Long id) {
        InvoiceResponseDto invoice = invoiceService.getInvoiceById(id);
        String targetPath = invoice.getFilePath() != null && !invoice.getFilePath().isBlank() ?
                invoice.getFilePath() : invoice.getFileName();
        Resource resource = fileStorageService.loadFileAsResource(targetPath);

        String mimeType = "application/octet-stream";
        if (invoice.getFileType() != null && invoice.getFileType().equalsIgnoreCase("PDF")) {
            mimeType = MediaType.APPLICATION_PDF_VALUE;
        } else if (invoice.getFileName() != null && invoice.getFileName().toLowerCase().endsWith(".png")) {
            mimeType = MediaType.IMAGE_PNG_VALUE;
        } else {
            mimeType = MediaType.IMAGE_JPEG_VALUE;
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + invoice.getFileName() + "\"")
                .body(resource);
    }

    @GetMapping("/files/{filename}")
    public ResponseEntity<Resource> serveDirectFile(@PathVariable("filename") String filename) {
        Resource resource = fileStorageService.loadFileAsResource(filename);
        String contentType = filename.toLowerCase().endsWith(".pdf") ? "application/pdf" : "image/jpeg";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel() throws IOException {
        byte[] excelBytes = excelExportService.generateExpenseReportExcel();
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String filename = "Bang_Ke_Chi_Phi_" + dateStr + ".xlsx";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(excelBytes);
    }
}
