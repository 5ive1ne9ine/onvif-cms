package com.mage.onvifcms.onvif;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

final class WsSecurity {
    private static final SecureRandom RANDOM = new SecureRandom();

    private WsSecurity() {}

    static String header(String username, String password) {
        if (username == null || username.isBlank()) return "";
        try {
            byte[] nonce = new byte[20];
            RANDOM.nextBytes(nonce);
            String created = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(nonce);
            sha1.update(created.getBytes(StandardCharsets.UTF_8));
            sha1.update((password == null ? "" : password).getBytes(StandardCharsets.UTF_8));
            String digest = Base64.getEncoder().encodeToString(sha1.digest());
            String encodedNonce = Base64.getEncoder().encodeToString(nonce);
            return """
                    <wsse:Security s:mustUnderstand="1"
                      xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
                      xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
                      <wsse:UsernameToken>
                        <wsse:Username>%s</wsse:Username>
                        <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest">%s</wsse:Password>
                        <wsse:Nonce EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary">%s</wsse:Nonce>
                        <wsu:Created>%s</wsu:Created>
                      </wsse:UsernameToken>
                    </wsse:Security>
                    """.formatted(XmlSupport.escape(username), digest, encodedNonce, created);
        } catch (Exception exception) {
            throw new OnvifException("无法生成 ONVIF WS-Security 请求头", exception);
        }
    }
}

