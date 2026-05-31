package com.acme.cms.common.util;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.symmetric.AES;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * AES-128 ECB PKCS5 加解密工具, 用于摄像头密码静态加密
 */
public class AesUtil {

    public static String encrypt(String plain, String key) {
        if (StrUtil.isEmpty(plain)) return plain;
        AES aes = new AES("ECB", "PKCS5Padding",
                new SecretKeySpec(normalize(key), "AES"));
        return Base64.encode(aes.encrypt(plain.getBytes(StandardCharsets.UTF_8)));
    }

    public static String decrypt(String cipher, String key) {
        if (StrUtil.isEmpty(cipher)) return cipher;
        try {
            AES aes = new AES("ECB", "PKCS5Padding",
                    new SecretKeySpec(normalize(key), "AES"));
            return new String(aes.decrypt(Base64.decode(cipher)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 兼容明文 (历史数据 / 首次未加密)
            return cipher;
        }
    }

    private static byte[] normalize(String key) {
        byte[] raw = key.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[16];
        for (int i = 0; i < 16; i++) {
            out[i] = i < raw.length ? raw[i] : 0;
        }
        return out;
    }
}
