package com.fsa.expense.service;

import com.fsa.expense.entity.Invoice;
import com.fsa.expense.entity.InvoiceLineItem;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class InvoiceCategoryClassifierTest {

    @Test
    void classifiesEquipmentAccurately() {
        Invoice invoice = Invoice.builder()
                .vendorName("CONG TY TNHH PHAT TRIEN VA CUNG UNG DICH VU")
                .lineItems(List.of(
                        InvoiceLineItem.builder().itemName("May chu Dell PowerEdge R750").build(),
                        InvoiceLineItem.builder().itemName("Cap mang Cat6 AMP/CommScope").build()
                ))
                .build();
        assertThat(InvoiceCategoryClassifier.classify(invoice)).isEqualTo(InvoiceCategoryClassifier.CATEGORY_EQUIPMENT);
    }

    @Test
    void classifiesTravelAccurately() {
        Invoice invoice = Invoice.builder()
                .vendorName("VIETNAM AIRLINES")
                .lineItems(List.of(
                        InvoiceLineItem.builder().itemName("Ve may bay HAN - SGN").build()
                ))
                .build();
        assertThat(InvoiceCategoryClassifier.classify(invoice)).isEqualTo(InvoiceCategoryClassifier.CATEGORY_TRAVEL);
    }

    @Test
    void classifiesMealsAccurately() {
        Invoice invoice = Invoice.builder()
                .vendorName("CONG TY CO PHAN PHUC LONG HERITAGE")
                .lineItems(List.of(
                        InvoiceLineItem.builder().itemName("Ca phe va banh ngot tiep khach").build()
                ))
                .build();
        assertThat(InvoiceCategoryClassifier.classify(invoice)).isEqualTo(InvoiceCategoryClassifier.CATEGORY_MEALS);
    }

    @Test
    void classifiesTrainingAccurately() {
        Invoice invoice = Invoice.builder()
                .vendorName("TRUONG DAI HOC FPT")
                .lineItems(List.of(
                        InvoiceLineItem.builder().itemName("Hoc phi dao tao khoa CNTT").build()
                ))
                .build();
        assertThat(InvoiceCategoryClassifier.classify(invoice)).isEqualTo(InvoiceCategoryClassifier.CATEGORY_TRAINING);
    }

    @Test
    void classifiesServicesAccurately() {
        Invoice invoice = Invoice.builder()
                .vendorName("CONG TY PHAN MEM VA TICH HOP HE THONG")
                .lineItems(List.of(
                        InvoiceLineItem.builder().itemName("Dich vu Cloud Server tieu chuan").build(),
                        InvoiceLineItem.builder().itemName("Dich vu bao tri he thong quy").build()
                ))
                .build();
        assertThat(InvoiceCategoryClassifier.classify(invoice)).isEqualTo(InvoiceCategoryClassifier.CATEGORY_SERVICES);
    }
}
