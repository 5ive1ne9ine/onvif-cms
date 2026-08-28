package com.mage.onvifcms.security;

import com.mage.onvifcms.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class CredentialCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    public CredentialCipher(AppProperties properties) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(properties.security().encryptionKey().getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("无法初始化凭据加密", exception);
        }
    }

    public String encrypt(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("无法加密摄像头凭据", exception);
        }
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            byte[] payload = Base64.getDecoder().decode(value);
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[payload.length - iv.length];
            System.arraycopy(payload, 0, iv, 0, iv.length);
            System.arraycopy(payload, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("无法解密摄像头凭据，请检查 APP_SECURITY_KEY", exception);
        }
    }
}

