package com.fsa.expense.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Business identity shared by XML, PDF and email intake, within one tenant. */
public final class InvoiceIdentity {
    private InvoiceIdentity() {}

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public static String key(String sellerTaxCode, String series, String number) {
        String tax = normalize(sellerTaxCode), symbol = normalize(series), no = normalize(number);
        if (tax.isEmpty() || symbol.isEmpty() || no.isEmpty()) return null;
        return AuditValidationService.computeSha256((tax + "|" + symbol + "|" + no).getBytes(StandardCharsets.UTF_8));
    }
}
