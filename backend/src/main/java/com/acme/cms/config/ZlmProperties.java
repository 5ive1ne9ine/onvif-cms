package com.acme.cms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "zlm")
public class ZlmProperties {
    private String baseUrl;
    private String secret;
    private String defaultApp = "live";
    private int rtpType = 0;
    private String hookToken = "internal";
    private Webrtc webrtc = new Webrtc();

    @Data
    public static class Webrtc {
        private String externIp = "127.0.0.1";
        private String playType = "play";
    }
}
