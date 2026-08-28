package com.mage.onvifcms.security;

import com.mage.onvifcms.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialCipherTest {

    @Test
    void encryptsWithRandomNonceAndDecrypts() {
        AppProperties properties = new AppProperties(null, null, null, new AppProperties.Security("unit-test-key"));
        CredentialCipher cipher = new CredentialCipher(properties);

        String first = cipher.encrypt("camera-password");
        String second = cipher.encrypt("camera-password");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("camera-password");
        assertThat(cipher.decrypt(second)).isEqualTo("camera-password");
    }
}

