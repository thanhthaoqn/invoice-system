package com.fsa.expense.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class InvoiceIdentityTest {

    @Test
    void crossFormatSeriesCanonicalizationMatches() {
        // XML has template "1" and series "2C26TYY"
        String keyXml = InvoiceIdentity.key("7790827231-626", "1", "2C26TYY", "0000100");

        // PDF has template "2" and series "2C26TYY"
        String keyPdf = InvoiceIdentity.key("7790827231-626", "2", "2C26TYY", "0000100");

        // Another PDF might have template null or series "C26TYY"
        String keyBare = InvoiceIdentity.key("7790827231-626", null, "C26TYY", "0000100");

        assertThat(keyXml).isNotNull();
        assertThat(keyPdf).isEqualTo(keyXml);
        assertThat(keyBare).isEqualTo(keyXml);
    }

    @Test
    void standardCircular78Normalization() {
        // 1C26TAA with template 1
        String key1 = InvoiceIdentity.key("0100000001", "1", "1C26TAA", "123");
        // C26TAA with template 1
        String key2 = InvoiceIdentity.key("0100000001", "1", "C26TAA", "0000123");
        // 1C26TAA without template
        String key3 = InvoiceIdentity.key("0100000001", null, "1C26TAA", "000123");

        assertThat(key1).isNotNull();
        assertThat(key2).isEqualTo(key1);
        assertThat(key3).isEqualTo(key1);
    }

    @Test
    void differentSeriesDoNotMatch() {
        String key1 = InvoiceIdentity.key("0100000001", "1", "1C26TAA", "100");
        String key2 = InvoiceIdentity.key("0100000001", "1", "1C26TBB", "100");

        assertThat(key1).isNotEqualTo(key2);
    }
}
