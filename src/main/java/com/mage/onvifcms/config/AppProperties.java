package com.mage.onvifcms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Discovery discovery,
        Ffmpeg ffmpeg,
        Mage mage,
        Security security
) {
    public AppProperties {
        discovery = discovery == null ? new Discovery(30, 1800) : discovery;
        ffmpeg = ffmpeg == null ? new Ffmpeg("ffmpeg", 8, 1280, 8) : ffmpeg;
        mage = mage == null ? new Mage(true, "http://localhost:30000/v1", "microsoft/Mage-VL", 6, 8, 180) : mage;
        security = security == null ? new Security("change-me-in-production") : security;
    }

    public record Discovery(int intervalSeconds, int timeoutMillis) {}

    public record Ffmpeg(String executable, int previewFps, int previewWidth, int sampleSeconds) {}

    public record Mage(boolean enabled, String baseUrl, String model, int maxFrames,
                       int maxConcurrentAnalyses, int requestTimeoutSeconds) {}

    public record Security(String encryptionKey) {}
}

