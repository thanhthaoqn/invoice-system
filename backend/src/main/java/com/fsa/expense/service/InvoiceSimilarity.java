package com.fsa.expense.service;

import com.fsa.expense.entity.Invoice;
import java.math.BigDecimal;
import java.util.Locale;

/**
 * Thuật toán tính toán độ tương đồng và phát hiện trùng lặp hóa đơn (Exact & Fuzzy Matching >= 90%).
 */
public final class InvoiceSimilarity {
    private InvoiceSimilarity() {}

    public record MatchResult(Invoice candidate, int score, String reason) {}

    public static String normalizeDigits(String val) {
        return val == null ? "" : val.replaceAll("[^0-9]", "");
    }

    public static String normalizeText(String val) {
        return val == null ? "" : val.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public static MatchResult compare(Invoice target, Invoice existing) {
        if (target == null || existing == null) return null;

        // 1. Exact Identity theo chuẩn Thông tư 78 (100%)
        String keyA = InvoiceIdentity.key(target.getTaxCode(), target.getInvoiceTemplate(), target.getInvoiceSeries(), target.getInvoiceNumber());
        String keyB = InvoiceIdentity.key(existing.getTaxCode(), existing.getInvoiceTemplate(), existing.getInvoiceSeries(), existing.getInvoiceNumber());
        if (keyA != null && keyA.equals(keyB)) {
            return new MatchResult(existing, 100, "Trùng 100% định danh hóa đơn (MST, Ký hiệu và Số HĐ)");
        }

        String taxA = normalizeDigits(target.getTaxCode());
        String taxB = normalizeDigits(existing.getTaxCode());
        boolean taxMatch = !taxA.isEmpty() && taxA.equals(taxB);

        String noA = normalizeDigits(target.getInvoiceNumber());
        String noB = normalizeDigits(existing.getInvoiceNumber());
        boolean numberMatch = false;
        if (!noA.isEmpty() && !noB.isEmpty()) {
            try {
                numberMatch = Long.parseLong(noA) == Long.parseLong(noB);
            } catch (NumberFormatException e) {
                numberMatch = noA.equals(noB);
            }
        }

        BigDecimal amtA = target.getTotalAmount();
        BigDecimal amtB = existing.getTotalAmount();
        boolean amountMatch = amtA != null && amtB != null && amtA.subtract(amtB).abs().doubleValue() < 1.0;

        String dateA = target.getInvoiceDate() != null ? target.getInvoiceDate().toString() : "";
        String dateB = existing.getInvoiceDate() != null ? existing.getInvoiceDate().toString() : "";
        boolean dateMatch = !dateA.isEmpty() && dateA.equals(dateB);

        String vendorA = normalizeText(target.getVendorName());
        String vendorB = normalizeText(existing.getVendorName());
        boolean vendorMatch = !vendorA.isEmpty() && !vendorB.isEmpty() && 
                (vendorA.equals(vendorB) || vendorA.contains(vendorB) || vendorB.contains(vendorA));

        // 2. Cùng MST bên bán + Cùng Số hóa đơn (95%)
        if (taxMatch && numberMatch) {
            return new MatchResult(existing, 95, "Trùng MST người bán (" + target.getTaxCode() + ") và Số hóa đơn (" + target.getInvoiceNumber() + ")");
        }

        // 3. Cùng MST bên bán + Cùng Tổng tiền + Cùng Ngày lập (92%)
        if (taxMatch && amountMatch && dateMatch) {
            return new MatchResult(existing, 92, "Trùng MST người bán, Ngày lập và Tổng số tiền");
        }

        // 4. Cùng Số hóa đơn + Cùng Tổng tiền + Đơn vị bán tương đồng (90%)
        if (numberMatch && amountMatch && (vendorMatch || taxMatch)) {
            return new MatchResult(existing, 90, "Trùng Số hóa đơn, Tổng tiền và Đơn vị bán");
        }

        return null;
    }
}
