package com.fsa.expense.intake;

import com.fsa.expense.entity.Invoice;
import com.fsa.expense.service.AuditValidationService;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Canonical business identity shared by XML and OCR/PDF intake. */
public final class InvoiceIdentity {
    private InvoiceIdentity() {}

    public static String hash(String taxCode, String template, String series, String number) {
        return AuditValidationService.computeSha256(
            canonical(taxCode, template, series, number).getBytes(StandardCharsets.UTF_8)
        );
    }

    public static boolean matches(Invoice invoice, String taxCode, String template, String series, String number) {
        return canonical(invoice.getTaxCode(), invoice.getInvoiceTemplate(), invoice.getInvoiceSeries(), invoice.getInvoiceNumber())
            .equals(canonical(taxCode, template, series, number));
    }

    static String canonical(String taxCode, String template, String series, String number) {
        String normalizedTaxCode = compact(taxCode);
        String normalizedTemplate = compact(template);
        String normalizedSeries = compact(series);
        String normalizedNumber = compact(number);

        // OCR sometimes prefixes the numeric invoice-template code to KHHDon.
        if (normalizedTemplate.matches("[0-9]{1,2}")
                && normalizedSeries.startsWith(normalizedTemplate)
                && normalizedSeries.length() > normalizedTemplate.length()) {
            normalizedSeries = normalizedSeries.substring(normalizedTemplate.length());
        }
        // SHDon is numeric in the supported Vietnamese e-invoice schema; leading zeroes
        // are presentation-only and are frequently added by OCR.
        if (normalizedNumber.matches("[0-9]+")) {
            normalizedNumber = normalizedNumber.replaceFirst("^0+(?!$)", "");
        }
        return String.join("|", normalizedTaxCode, normalizedTemplate, normalizedSeries, normalizedNumber);
    }

    private static String compact(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
