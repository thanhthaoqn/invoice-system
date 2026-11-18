package com.fsa.expense.service;

import com.fsa.expense.entity.Invoice;
import com.fsa.expense.entity.InvoiceLineItem;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Intelligent categorization of invoices based on goods, services, and vendor information.
 */
public final class InvoiceCategoryClassifier {

    public static final String CATEGORY_TRAVEL = "Chi phí Công tác";
    public static final String CATEGORY_MEALS = "Tiếp khách / Ăn uống";
    public static final String CATEGORY_EQUIPMENT = "Mua sắm Vật tư / Thiết bị";
    public static final String CATEGORY_TRAINING = "Đào tạo / Hội thảo";
    public static final String CATEGORY_SERVICES = "Chi phí Dịch vụ / Khác";

    private InvoiceCategoryClassifier() {}

    public static String classify(Invoice invoice) {
        if (invoice == null) return CATEGORY_SERVICES;
        StringBuilder sb = new StringBuilder();
        if (invoice.getVendorName() != null) {
            sb.append(invoice.getVendorName()).append(" ");
        }
        if (invoice.getLineItems() != null) {
            for (InvoiceLineItem item : invoice.getLineItems()) {
                if (item != null && item.getItemName() != null) {
                    sb.append(item.getItemName()).append(" ");
                }
            }
        }
        if (invoice.getNotes() != null) {
            sb.append(invoice.getNotes()).append(" ");
        }
        return classifyText(sb.toString());
    }

    public static String classify(String vendorName, List<InvoiceLineItem> lineItems) {
        StringBuilder sb = new StringBuilder();
        if (vendorName != null) {
            sb.append(vendorName).append(" ");
        }
        if (lineItems != null) {
            for (InvoiceLineItem item : lineItems) {
                if (item != null && item.getItemName() != null) {
                    sb.append(item.getItemName()).append(" ");
                }
            }
        }
        return classifyText(sb.toString());
    }

    public static String classifyText(String text) {
        if (text == null || text.isBlank()) {
            return CATEGORY_SERVICES;
        }
        String fullText = normalize(text);

        // 1. Chi phí Công tác (Vé máy bay, khách sạn, taxi di chuyển)
        if (matchesAny(fullText,
                "airline", "hang khong", "ve may bay", "chuyen bay", "flight",
                "khach san", "hotel", "resort", "homestay", "luu tru",
                "tau hoa", "duong sat", "ve tau", "ve xe", "xe khach",
                "taxi", "grab", "be group", "mai linh", "vinataxi", "xanh sm",
                "cong tac", "ve di chuyen")) {
            return CATEGORY_TRAVEL;
        }

        // 2. Tiếp khách / Ăn uống (Nhà hàng, cafe, đồ uống, tiếp khách)
        if (matchesAny(fullText,
                "nha hang", "restaurant", "quan an", "an uong", "tiep khach",
                "ca phe", "coffee", "highlands", "starbucks", "phuc long", "the coffee house",
                "tea break", "tra sua", "tiệc", "buffet", "mon an", "do uong",
                "bia", "ruou", "nuoc ngot", "banh ngot", "banh sinh nhat", "am thuc")) {
            return CATEGORY_MEALS;
        }

        // 3. Đào tạo / Hội thảo (Khóa học, hội thảo, học phí, sự kiện)
        if (matchesAny(fullText,
                "dao tao", "khoa hoc", "course", "training", "lop hoc", "hoc phi",
                "hoi thao", "seminar", "workshop", "conference", "ve su kien",
                "chung chi", "certificate", "dai hoc", "truong hoc", "fpt education",
                "hoc vien", "tap huan", "boi duong")) {
            return CATEGORY_TRAINING;
        }

        // 4. Mua sắm Vật tư / Thiết bị vs Chi phí Dịch vụ / Khác
        int hwScore = countMatches(fullText,
                "may chu", "server", "dell", "hp", "lenovo", "asus", "acer", "macbook",
                "man hinh", "monitor", "display", "screen",
                "chuot", "mouse", "logitech", "ban phim", "keyboard",
                "o cung", "ssd", "hdd", "nvme", "samsung", "kingston", "transcend",
                "cap mang", "day mang", "cat6", "cat5", "amp", "commscope",
                "thiet bi", "can bang tai", "draytek", "cisco", "mikrotik", "tp-link",
                "switch", "router", "modem", "access point", "wifi",
                "may tinh", "laptop", "pc", "linh kien", "ram", "cpu", "vga", "card do hoa",
                "may in", "printer", "canon", "epson", "brother", "may scan", "photocopy",
                "muc in", "cartridge", "giay in", "giay a4", "van phong pham", "vpp",
                "but", "so tay", "ghim", "kep", "tai nghe", "headphone", "loa", "webcam");

        int svcScore = countMatches(fullText,
                "cloud", "dien toan dam may", "server tieu chuan", "vps", "hosting", "ten mien", "domain",
                "dich vu", "service", "tu van", "consulting", "giai phap",
                "bao tri", "maintenance", "bao duong", "sua chua",
                "phan mem", "software", "ban quyen", "license", "subscription",
                "vien thong", "cuoc", "internet", "setup he thong", "trien khai");

        if (hwScore > 0 && hwScore >= svcScore) {
            return CATEGORY_EQUIPMENT;
        }

        if (svcScore > 0) {
            return CATEGORY_SERVICES;
        }

        if (hwScore > 0) {
            return CATEGORY_EQUIPMENT;
        }

        return CATEGORY_SERVICES;
    }

    private static boolean matchesAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private static int countMatches(String text, String... keywords) {
        int count = 0;
        for (String kw : keywords) {
            if (text.contains(kw)) count++;
        }
        return count;
    }

    private static String normalize(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                         .replace('đ', 'd').replace('Đ', 'D')
                         .toLowerCase(Locale.ROOT);
    }
}
