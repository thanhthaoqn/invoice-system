package com.fsa.expense.service;

import com.fsa.expense.entity.Invoice;
import com.fsa.expense.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
public class ExcelExportService {

    private final InvoiceRepository invoiceRepository;

    public ExcelExportService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    public byte[] generateExpenseReportExcel() throws IOException {
        List<Invoice> invoices = invoiceRepository.findAllByOrderByCreatedAtDesc();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Bảng Kê Chi Phí");

            // Fonts & Styles
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            titleFont.setColor(IndexedColors.DARK_BLUE.getIndex());

            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);

            Font subTitleFont = workbook.createFont();
            subTitleFont.setItalic(true);
            subTitleFont.setFontHeightInPoints((short) 11);

            CellStyle subTitleStyle = workbook.createCellStyle();
            subTitleStyle.setFont(subTitleFont);
            subTitleStyle.setAlignment(HorizontalAlignment.CENTER);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorders(headerStyle);

            DataFormat dataFormat = workbook.createDataFormat();
            CellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.setDataFormat(dataFormat.getFormat("#,##0 ₫"));
            setBorders(currencyStyle);

            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setAlignment(HorizontalAlignment.CENTER);
            setBorders(dateStyle);

            CellStyle defaultStyle = workbook.createCellStyle();
            setBorders(defaultStyle);

            Font totalFont = workbook.createFont();
            totalFont.setBold(true);
            CellStyle totalStyle = workbook.createCellStyle();
            totalStyle.setFont(totalFont);
            totalStyle.setDataFormat(dataFormat.getFormat("#,##0 ₫"));
            totalStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorders(totalStyle);

            // Row 0: Title
            Row titleRow = sheet.createRow(1);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("BẢNG KÊ CHỨNG TỪ CHI PHÍ QUYẾT TOÁN DOANH NGHIỆP");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 11));

            // Row 1: Subtitle
            Row subRow = sheet.createRow(2);
            Cell subCell = subRow.createCell(0);
            subCell.setCellValue("Ngày xuất báo cáo: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " | Hệ thống Smart Expense AI");
            subCell.setCellStyle(subTitleStyle);
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 11));

            // Row 4: Table Headers
            String[] headers = {
                    "STT", "Số Hóa Đơn", "Ngày Lập", "Đơn Vị Bán Hàng / Đối Tác",
                    "Mã Số Thuế", "Danh Mục Chi Phí", "Tiền Trước Thuế", "Tiền VAT",
                    "Tổng Thanh Toán", "Trạng Thái", "Người Nộp", "Người Duyệt"
            };

            Row headerRow = sheet.createRow(4);
            headerRow.setHeightInPoints(28);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data Rows
            int rowIdx = 5;
            int stt = 1;
            for (Invoice inv : invoices) {
                Row row = sheet.createRow(rowIdx++);
                
                Cell c0 = row.createCell(0);
                c0.setCellValue(stt++);
                c0.setCellStyle(defaultStyle);

                Cell c1 = row.createCell(1);
                c1.setCellValue(inv.getInvoiceNumber() != null ? inv.getInvoiceNumber() : "N/A");
                c1.setCellStyle(defaultStyle);

                Cell c2 = row.createCell(2);
                c2.setCellValue(inv.getInvoiceDate() != null ? inv.getInvoiceDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "");
                c2.setCellStyle(dateStyle);

                Cell c3 = row.createCell(3);
                c3.setCellValue(inv.getVendorName() != null ? inv.getVendorName() : "");
                c3.setCellStyle(defaultStyle);

                Cell c4 = row.createCell(4);
                c4.setCellValue(inv.getTaxCode() != null ? inv.getTaxCode() : "");
                c4.setCellStyle(defaultStyle);

                Cell c5 = row.createCell(5);
                c5.setCellValue(inv.getCategory() != null ? inv.getCategory() : "");
                c5.setCellStyle(defaultStyle);

                Cell c6 = row.createCell(6);
                c6.setCellValue(inv.getSubtotalAmount() != null ? inv.getSubtotalAmount().doubleValue() : 0.0);
                c6.setCellStyle(currencyStyle);

                Cell c7 = row.createCell(7);
                c7.setCellValue(inv.getVatAmount() != null ? inv.getVatAmount().doubleValue() : 0.0);
                c7.setCellStyle(currencyStyle);

                Cell c8 = row.createCell(8);
                c8.setCellValue(inv.getTotalAmount() != null ? inv.getTotalAmount().doubleValue() : 0.0);
                c8.setCellStyle(currencyStyle);

                Cell c9 = row.createCell(9);
                c9.setCellValue(inv.getStatus() != null ? inv.getStatus().name() : "");
                c9.setCellStyle(defaultStyle);

                Cell c10 = row.createCell(10);
                c10.setCellValue(inv.getSubmittedBy() != null ? inv.getSubmittedBy() : "");
                c10.setCellStyle(defaultStyle);

                Cell c11 = row.createCell(11);
                c11.setCellValue(inv.getReviewedBy() != null ? inv.getReviewedBy() : "");
                c11.setCellStyle(defaultStyle);
            }

            // Summary row
            if (rowIdx > 5) {
                Row summaryRow = sheet.createRow(rowIdx);
                Cell sumLabel = summaryRow.createCell(0);
                sumLabel.setCellValue("TỔNG CỘNG");
                sumLabel.setCellStyle(totalStyle);
                sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, 5));

                for (int col = 1; col <= 5; col++) {
                    summaryRow.createCell(col).setCellStyle(totalStyle);
                }

                Cell sumSubtotal = summaryRow.createCell(6);
                sumSubtotal.setCellFormula("SUM(G6:G" + rowIdx + ")");
                sumSubtotal.setCellStyle(totalStyle);

                Cell sumVat = summaryRow.createCell(7);
                sumVat.setCellFormula("SUM(H6:H" + rowIdx + ")");
                sumVat.setCellStyle(totalStyle);

                Cell sumTotal = summaryRow.createCell(8);
                sumTotal.setCellFormula("SUM(I6:I" + rowIdx + ")");
                sumTotal.setCellStyle(totalStyle);

                for (int col = 9; col <= 11; col++) {
                    summaryRow.createCell(col).setCellStyle(totalStyle);
                }
            }

            // Auto size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 1200, 3500));
            }

            workbook.write(out);
            log.info("Generated Expense Report Excel with {} invoices", invoices.size());
            return out.toByteArray();
        }
    }

    private void setBorders(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
