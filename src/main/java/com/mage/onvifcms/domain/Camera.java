package com.mage.onvifcms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cameras")
public class Camera {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stable_key", nullable = false, unique = true, length = 512)
    private String stableKey;

    @Column(name = "endpoint_urn", length = 512)
    private String endpointUrn;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 255)
    private String host;

    @Column(name = "device_service_url", nullable = false, length = 1000)
    private String deviceServiceUrl;

    @Column(name = "media_service_url", length = 1000)
    private String mediaServiceUrl;

    @Column(name = "ptz_service_url", length = 1000)
    private String ptzServiceUrl;

    @Column(length = 160)
    private String manufacturer;

    @Column(length = 160)
    private String model;

    @Column(name = "firmware_version", length = 160)
    private String firmwareVersion;

    @Column(name = "serial_number", length = 160)
    private String serialNumber;

    @Column(length = 160)
    private String username;

    @Column(name = "encrypted_password", columnDefinition = "TEXT")
    private String encryptedPassword;

    @Column(name = "profile_token", length = 512)
    private String profileToken;

    @Column(name = "rtsp_uri", length = 2000)
    private String rtspUri;

    @Column(name = "ptz_supported", nullable = false)
    private boolean ptzSupported;

    @Column(nullable = false)
    private boolean online;

    @Column(name = "detection_enabled", nullable = false)
    private boolean detectionEnabled;

    @Column(name = "detection_prompt", columnDefinition = "TEXT")
    private String detectionPrompt;

    @Column(name = "detection_interval_seconds", nullable = false)
    private int detectionIntervalSeconds = 30;

    @Column(name = "confidence_threshold", nullable = false, precision = 4, scale = 3)
    private BigDecimal confidenceThreshold = BigDecimal.valueOf(0.6);

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "last_connected_at")
    private Instant lastConnectedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    public Long getId() { return id; }
    public String getStableKey() { return stableKey; }
    public void setStableKey(String stableKey) { this.stableKey = stableKey; }
    public String getEndpointUrn() { return endpointUrn; }
    public void setEndpointUrn(String endpointUrn) { this.endpointUrn = endpointUrn; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public String getDeviceServiceUrl() { return deviceServiceUrl; }
    public void setDeviceServiceUrl(String deviceServiceUrl) { this.deviceServiceUrl = deviceServiceUrl; }
    public String getMediaServiceUrl() { return mediaServiceUrl; }
    public void setMediaServiceUrl(String mediaServiceUrl) { this.mediaServiceUrl = mediaServiceUrl; }
    public String getPtzServiceUrl() { return ptzServiceUrl; }
    public void setPtzServiceUrl(String ptzServiceUrl) { this.ptzServiceUrl = ptzServiceUrl; }
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEncryptedPassword() { return encryptedPassword; }
    public void setEncryptedPassword(String encryptedPassword) { this.encryptedPassword = encryptedPassword; }
    public String getProfileToken() { return profileToken; }
    public void setProfileToken(String profileToken) { this.profileToken = profileToken; }
    public String getRtspUri() { return rtspUri; }
    public void setRtspUri(String rtspUri) { this.rtspUri = rtspUri; }
    public boolean isPtzSupported() { return ptzSupported; }
    public void setPtzSupported(boolean ptzSupported) { this.ptzSupported = ptzSupported; }
    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }
    public boolean isDetectionEnabled() { return detectionEnabled; }
    public void setDetectionEnabled(boolean detectionEnabled) { this.detectionEnabled = detectionEnabled; }
    public String getDetectionPrompt() { return detectionPrompt; }
    public void setDetectionPrompt(String detectionPrompt) { this.detectionPrompt = detectionPrompt; }
    public int getDetectionIntervalSeconds() { return detectionIntervalSeconds; }
    public void setDetectionIntervalSeconds(int detectionIntervalSeconds) { this.detectionIntervalSeconds = detectionIntervalSeconds; }
    public BigDecimal getConfidenceThreshold() { return confidenceThreshold; }
    public void setConfidenceThreshold(BigDecimal confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public Instant getLastConnectedAt() { return lastConnectedAt; }
    public void setLastConnectedAt(Instant lastConnectedAt) { this.lastConnectedAt = lastConnectedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

