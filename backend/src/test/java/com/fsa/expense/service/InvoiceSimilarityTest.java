package com.fsa.expense.service;

import com.fsa.expense.entity.Invoice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceSimilarityTest {

    @Test
    void shouldDetectExactIdentityMatch() {
        Invoice a = new Invoice();
        a.setTaxCode("7790827231-626");
        a.setInvoiceSeries("2C26TYY");
        a.setInvoiceNumber("0000100");
        a.setInvoiceTemplate("1");

        Invoice b = new Invoice();
        b.setTaxCode("7790827231626");
        b.setInvoiceSeries("2C26TYY");
        b.setInvoiceNumber("100");
        b.setInvoiceTemplate("1");

        var result = InvoiceSimilarity.compare(a, b);
        assertThat(result).isNotNull();
        assertThat(result.score()).isEqualTo(100);
    }

    @Test
    void shouldDetectFuzzyMatchSameTaxAndNumber() {
        Invoice a = new Invoice();
        a.setTaxCode("7790827231-626");
        a.setInvoiceNumber("0000100");
        a.setInvoiceSeries("2C26TYY"); // khác series hoặc khác template

        Invoice b = new Invoice();
        b.setTaxCode("7790827231626");
        b.setInvoiceNumber("100");
        b.setInvoiceSeries("1C26TYY"); // OCR nhận diện nhầm ký hiệu

        var result = InvoiceSimilarity.compare(a, b);
        assertThat(result).isNotNull();
        assertThat(result.score()).isGreaterThanOrEqualTo(95);
    }

    @Test
    void shouldDetectFuzzyMatchSameTaxDateAndTotalAmount() {
        Invoice a = new Invoice();
        a.setTaxCode("7790827231-626");
        a.setInvoiceNumber("0000100");
        a.setInvoiceDate(LocalDate.of(2026, 9, 20));
        a.setTotalAmount(new BigDecimal("48601182000"));

        Invoice b = new Invoice();
        b.setTaxCode("7790827231626");
        b.setInvoiceNumber("0000101"); // Số HĐ lệch nhẹ
        b.setInvoiceDate(LocalDate.of(2026, 9, 20));
        b.setTotalAmount(new BigDecimal("48601182000"));

        var result = InvoiceSimilarity.compare(a, b);
        assertThat(result).isNotNull();
        assertThat(result.score()).isGreaterThanOrEqualTo(92);
    }

    @Test
    void shouldNotFlagDistinctInvoices() {
        Invoice a = new Invoice();
        a.setTaxCode("0100109106");
        a.setInvoiceNumber("0000001");
        a.setTotalAmount(new BigDecimal("1000000"));

        Invoice b = new Invoice();
        b.setTaxCode("0300000002");
        b.setInvoiceNumber("0000002");
        b.setTotalAmount(new BigDecimal("2000000"));

        var result = InvoiceSimilarity.compare(a, b);
        assertThat(result).isNull();
    }
}
