package com.acme.cms.camera.onvif;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.RandomUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * ONVIF WS-Security UsernameToken (Digest) 头部构造
 * 参考: OASIS WS-Security UsernameToken Profile 1.1
 *
 * PasswordDigest = Base64( SHA1( Base64Decode(Nonce) + Created + Password ) )
 */
public class WsSecurityHeader {

    public static String build(String username, String password) {
        if (username == null) username = "";
        if (password == null) password = "";

        byte[] nonceBytes = RandomUtil.randomBytes(16);
        String nonceB64 = Base64.encode(nonceBytes);
        String created = Instant.now().toString();   // ISO-8601 UTC

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(nonceBytes);
            md.update(created.getBytes(StandardCharsets.UTF_8));
            md.update(password.getBytes(StandardCharsets.UTF_8));
            String digest = Base64.encode(md.digest());

            return "<wsse:Security xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\""
                    + " xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">"
                    + "<wsse:UsernameToken>"
                    + "<wsse:Username>" + xml(username) + "</wsse:Username>"
                    + "<wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">"
                    + digest + "</wsse:Password>"
                    + "<wsse:Nonce EncodingType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary\">"
                    + nonceB64 + "</wsse:Nonce>"
                    + "<wsu:Created>" + created + "</wsu:Created>"
                    + "</wsse:UsernameToken>"
                    + "</wsse:Security>";
        } catch (Exception e) {
            throw new RuntimeException("WS-Security header build failed", e);
        }
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
