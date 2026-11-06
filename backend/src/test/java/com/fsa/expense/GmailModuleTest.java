package com.fsa.expense;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsa.expense.gmail.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class GmailModuleTest {
    @Test void refreshTokenEncryptionRoundTripUsesRandomAuthenticatedCiphertext() {
        GmailProperties properties=new GmailProperties();
        properties.setTokenEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        TokenCipher cipher=new TokenCipher(properties);
        String one=cipher.encrypt("refresh-token"), two=cipher.encrypt("refresh-token");
        assertThat(one).startsWith("v1:").isNotEqualTo(two);
        assertThat(cipher.decrypt(one)).isEqualTo("refresh-token");
        assertThatThrownBy(() -> cipher.decrypt(one.substring(0,one.length()-2)+"aa")).isInstanceOf(IllegalStateException.class);
    }

    @Test void nestedMimePartsAndInlineAttachmentAreParsed() throws Exception {
        String xml="<HDon/>";
        String encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
        String json="""
            {"id":"gmail-1","threadId":"thread-1","internalDate":"1793836800000","payload":{
              "headers":[{"name":"From","value":"supplier@example.com"},{"name":"Subject","value":"Invoice"},{"name":"Message-ID","value":"<rfc-1@example.com>"}],
              "parts":[{"partId":"1","mimeType":"multipart/mixed","parts":[
                {"partId":"1.1","filename":"invoice.xml","mimeType":"application/xml","body":{"size":8,"data":"%s"}}]}]}}
            """.formatted(encoded);
        var root=new ObjectMapper().readTree(json);
        var data=new GmailMimeExtractor().read(root);
        assertThat(data.id()).isEqualTo("gmail-1");
        assertThat(data.rfcMessageId()).isEqualTo("<rfc-1@example.com>");
        assertThat(data.parts()).singleElement().satisfies(part -> {
            assertThat(part.fileName()).isEqualTo("invoice.xml");
            assertThat(new String(GmailApiClient.decode(part.inlineData()),StandardCharsets.UTF_8)).isEqualTo(xml);
        });
    }
}
