package com.fsa.expense.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.*;
import java.util.Base64;

public final class Passwords {
    private Passwords() {}
    public static String hash(String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return "210000:" + Base64.getEncoder().encodeToString(salt) + ":" + derive(password, salt, 210000);
    }
    public static boolean matches(String password, String encoded) {
        try {
            String[] parts = encoded.split(":");
            String actual = derive(password, Base64.getDecoder().decode(parts[1]), Integer.parseInt(parts[0]));
            return MessageDigest.isEqual(actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII), parts[2].getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        } catch (RuntimeException e) { return false; }
    }
    private static String derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        try { return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded()); }
        catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
        finally { spec.clearPassword(); }
    }
}
