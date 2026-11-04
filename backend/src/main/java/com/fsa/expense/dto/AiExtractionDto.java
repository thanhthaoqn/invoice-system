package com.fsa.expense.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiExtractionDto {
    @JsonProperty("invoice_template") private String invoiceTemplate;
    @JsonProperty("invoice_series") private String invoiceSeries;
    @JsonProperty("seller_address") private String sellerAddress;
    @JsonProperty("buyer_name") private String buyerName;
    @JsonProperty("buyer_tax_code") private String buyerTaxCode;
    @JsonProperty("buyer_address") private String buyerAddress;
    private String currency;

    @JsonProperty("invoice_number")
    private String invoiceNumber;

    @JsonProperty("invoice_date")
    private String invoiceDate;

    @JsonProperty("vendor_name")
    private String vendorName;

    @JsonProperty("tax_code")
    private String taxCode;

    @JsonProperty("line_items")
    @Builder.Default
    private List<AiLineItemDto> lineItems = new ArrayList<>();

    @JsonProperty("subtotal_amount")
    private BigDecimal subtotalAmount;

    @JsonProperty("vat_amount")
    private BigDecimal vatAmount;

    @JsonProperty("total_amount")
    private BigDecimal totalAmount;

    @JsonProperty("category")
    private String category;

    @JsonProperty("confidence_score")
    private Double confidenceScore;

    @JsonProperty("notes")
    private String notes;

    @JsonProperty("raw_text")
    private String rawText;

    @JsonProperty("extraction_method")
    private String extractionMethod;

    @JsonProperty("text_layer_detected")
    private Boolean textLayerDetected;

    @JsonProperty("debit_account")
    private String debitAccount;

    @JsonProperty("vat_account")
    private String vatAccount;

    @JsonProperty("credit_account")
    private String creditAccount;

    @JsonProperty("cashless_required")
    private Boolean cashlessRequired;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AiLineItemDto {
        @JsonProperty("item_name")
        private String itemName;

        @JsonProperty("quantity")
        private BigDecimal quantity;

        @JsonProperty("unit_price")
        private BigDecimal unitPrice;

        @JsonProperty("amount")
        private BigDecimal amount;

        @JsonProperty("vat_rate")
        private String vatRate;
    }
}
