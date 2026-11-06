package com.fsa.expense.gmail;

import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class TokenCipher {
    private static final int NONCE_BYTES = 12;
    private final GmailProperties properties;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(GmailProperties properties) { this.properties = properties; }

    public String encrypt(String plainText) {
        try {
            byte[] key = key();
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(encrypted, 0, combined, nonce.length, encrypted.length);
            return "v1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Không mã hóa được Gmail token", e);
        }
    }

    public String decrypt(String value) {
        try {
            if (value == null || !value.startsWith("v1:")) throw new IllegalArgumentException("Token format không hợp lệ");
            byte[] combined = Base64.getUrlDecoder().decode(value.substring(3));
            if (combined.length <= NONCE_BYTES) throw new IllegalArgumentException("Token bị hỏng");
            byte[] nonce = java.util.Arrays.copyOfRange(combined, 0, NONCE_BYTES);
            byte[] encrypted = java.util.Arrays.copyOfRange(combined, NONCE_BYTES, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Không giải mã được Gmail token", e);
        }
    }

    private byte[] key() {
        byte[] key;
        try { key = Base64.getDecoder().decode(properties.getTokenEncryptionKey()); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("GMAIL_TOKEN_ENCRYPTION_KEY phải là Base64", e); }
        if (key.length != 32) throw new IllegalStateException("GMAIL_TOKEN_ENCRYPTION_KEY phải giải mã thành đúng 32 byte");
        return key;
    }
}
