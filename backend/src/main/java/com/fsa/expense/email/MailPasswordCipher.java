package com.fsa.expense.email;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.SecureRandom;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class MailPasswordCipher {
    private final String configuredKey;
    public MailPasswordCipher(@Value("${app.email.encryption-key:}") String key){configuredKey=key;}
    public boolean configured(){return !configuredKey.isBlank();}
    private SecretKeySpec key(){
        try {byte[] bytes=Base64.getDecoder().decode(configuredKey);if(bytes.length!=32)throw new IllegalArgumentException();return new SecretKeySpec(bytes,"AES");}
        catch(Exception e){throw new IllegalStateException("EMAIL_ENCRYPTION_KEY phải là khóa 32 byte mã hóa Base64");}
    }
    public String encrypt(String plain,Long tenantId){
        try {
            byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(),new GCMParameterSpec(128,iv));
            cipher.updateAAD(tenantId.toString().getBytes(StandardCharsets.UTF_8));
            byte[] encrypted=cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length+encrypted.length).put(iv).put(encrypted).array());
        } catch(IllegalStateException e){throw e;}catch(Exception e){throw new IllegalStateException("Không mã hóa được mật khẩu hộp thư",e);}
    }
    public String decrypt(String encoded,Long tenantId){
        try {
            byte[] bytes=Base64.getDecoder().decode(encoded);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,bytes,0,12));
            cipher.updateAAD(tenantId.toString().getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(bytes,12,bytes.length-12),StandardCharsets.UTF_8);
        }catch(Exception e){throw new IllegalStateException("Không giải mã được mật khẩu hộp thư; kiểm tra khóa mã hóa",e);}
    }
}
