package com.acme.cms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "cms.jwt")
public class JwtProperties {
    private String secret;
    private int expireMinutes = 720;
    private String header = "Authorization";
    private String prefix = "Bearer ";
}
