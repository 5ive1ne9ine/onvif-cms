package com.mage.onvifcms.api;

import com.mage.onvifcms.domain.Camera;

import java.math.BigDecimal;
import java.time.Instant;

public record CameraView(
        Long id,
        String name,
        String host,
        String deviceServiceUrl,
        String manufacturer,
        String model,
        String firmwareVersion,
        String serialNumber,
        boolean credentialsConfigured,
        boolean streamConfigured,
        boolean ptzSupported,
        boolean online,
        boolean detectionEnabled,
        String detectionPrompt,
        int detectionIntervalSeconds,
        BigDecimal confidenceThreshold,
        Instant lastSeenAt,
        Instant lastConnectedAt
) {
    public static CameraView from(Camera camera) {
        return new CameraView(camera.getId(), camera.getName(), camera.getHost(), camera.getDeviceServiceUrl(),
                camera.getManufacturer(), camera.getModel(), camera.getFirmwareVersion(), camera.getSerialNumber(),
                camera.getUsername() != null && !camera.getUsername().isBlank(),
                camera.getRtspUri() != null && !camera.getRtspUri().isBlank(), camera.isPtzSupported(),
                camera.isOnline(), camera.isDetectionEnabled(), camera.getDetectionPrompt(),
                camera.getDetectionIntervalSeconds(), camera.getConfidenceThreshold(),
                camera.getLastSeenAt(), camera.getLastConnectedAt());
    }
}

