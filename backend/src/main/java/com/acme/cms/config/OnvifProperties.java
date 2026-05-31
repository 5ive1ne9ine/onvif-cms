package com.acme.cms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "cms.onvif")
public class OnvifProperties {
    private int discoveryTimeoutMs = 4000;
    private int pullIntervalSeconds = 5;
    private int subscriptionTtlSeconds = 60;
    private String aesKey;
}
