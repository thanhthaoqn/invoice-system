package com.fsa.expense.intake;

import com.fsa.expense.dto.AiExtractionDto;
import com.fsa.expense.entity.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class PdfInvoiceMapper {
    private String text(String value, int length, String field, boolean required) {
        String clean=value == null ? "" : value.trim();
        if ((required && clean.isBlank()) || clean.length() > length) throw new IllegalArgumentException("Kiểm tra trường " + field);
        return clean.isEmpty() ? null : clean;
    }
    private BigDecimal amount(BigDecimal value, String field, boolean required, int digits) {
        if (value == null) {
            if (required) throw new IllegalArgumentException("Thiếu " + field);
            return null;
        }
        if (value.scale()>2 || value.precision()-value.scale()>digits) throw new IllegalArgumentException("Giá trị " + field + " vượt giới hạn số");
        return value;
    }
    public Invoice invoice(AiExtractionDto data) {
        Invoice invoice=Invoice.builder()
            .invoiceNumber(text(data.getInvoiceNumber(),50,"số hóa đơn",true))
            .invoiceTemplate(text(data.getInvoiceTemplate(),50,"mẫu số",true))
            .invoiceSeries(text(data.getInvoiceSeries(),50,"ký hiệu",true))
            .vendorName(text(data.getVendorName(),255,"tên bên bán",true))
            .taxCode(text(data.getTaxCode(),50,"MST bên bán",true))
            .buyerName(text(data.getBuyerName(),255,"tên bên mua",false))
            .buyerTaxCode(text(data.getBuyerTaxCode(),50,"MST bên mua",false))
            .buyerAddress(text(data.getBuyerAddress(),1000,"địa chỉ bên mua",false))
            .sellerAddress(text(data.getSellerAddress(),1000,"địa chỉ bên bán",false))
            .currency(text(data.getCurrency(),10,"tiền tệ",false))
            .subtotalAmount(amount(data.getSubtotalAmount(),"tổng trước thuế",true,13))
            .vatAmount(amount(data.getVatAmount(),"tiền thuế",true,13))
            .totalAmount(amount(data.getTotalAmount(),"tổng thanh toán",true,13))
            .category("Chi phí Dịch vụ / Khác").fileType("PDF").source("OCR_CONFIRMED")
            .extractionMethod(data.getExtractionMethod()).status(InvoiceStatus.PENDING_REVIEW).validationState("NOT_CHECKED").build();
        try { invoice.setInvoiceDate(LocalDate.parse(data.getInvoiceDate())); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Ngày lập cần có định dạng YYYY-MM-DD"); }
        if (data.getLineItems()!=null) {
            if (data.getLineItems().size()>1000) throw new IllegalArgumentException("Quá nhiều dòng hàng");
            for (var line : data.getLineItems()) {
                invoice.getLineItems().add(InvoiceLineItem.builder().invoice(invoice)
                    .itemName(text(line.getItemName(),255,"tên hàng",true))
                    .quantity(amount(line.getQuantity(),"số lượng",false,8))
                    .unitPrice(amount(line.getUnitPrice(),"đơn giá",false,13))
                    .amount(amount(line.getAmount(),"thành tiền",true,13))
                    .vatRate(text(line.getVatRate(),20,"thuế suất",false))
                    .lineNumber(invoice.getLineItems().size()+1).build());
            }
        }
        invoice.setIdentityKey(com.fsa.expense.service.InvoiceIdentity.key(invoice.getTaxCode(), invoice.getInvoiceSeries(), invoice.getInvoiceNumber()));
        return invoice;
    }
}
