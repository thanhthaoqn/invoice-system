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
        return key(sellerTaxCode, null, series, number);
    }

    public static String key(String sellerTaxCode, String template, String series, String number) {
        String tax = normalize(sellerTaxCode).replaceAll("[^A-Z0-9]", "");
        String symbol = normalize(series).replaceAll("[^A-Z0-9]", "");
        String no = normalize(number).replaceAll("[^A-Z0-9]", "");
        String prefix = normalize(template).replaceAll("[^A-Z0-9]", "");
        if (prefix.matches("[0-9]{1,2}") && symbol.startsWith(prefix) && symbol.length() > prefix.length()) {
            symbol = symbol.substring(prefix.length());
        }
        if (no.matches("[0-9]+")) no = no.replaceFirst("^0+(?!$)", "");
        if (tax.isEmpty() || symbol.isEmpty() || no.isEmpty()) return null;
        return AuditValidationService.computeSha256((tax + "|" + symbol + "|" + no).getBytes(StandardCharsets.UTF_8));
    }
}
