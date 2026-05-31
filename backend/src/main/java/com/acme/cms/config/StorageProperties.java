package com.acme.cms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "cms.storage")
public class StorageProperties {
    private String recordDir;
    private String snapshotDir;
    private int segmentCacheSeconds = 60;
}
