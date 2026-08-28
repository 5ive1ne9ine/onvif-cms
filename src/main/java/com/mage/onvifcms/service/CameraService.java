package com.mage.onvifcms.service;

import com.mage.onvifcms.api.ApiException;
import com.mage.onvifcms.api.CameraView;
import com.mage.onvifcms.domain.Camera;
import com.mage.onvifcms.onvif.DiscoveredDevice;
import com.mage.onvifcms.onvif.OnvifException;
import com.mage.onvifcms.onvif.OnvifSoapClient;
import com.mage.onvifcms.repository.CameraRepository;
import com.mage.onvifcms.security.CredentialCipher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;

@Service
public class CameraService {
    private static final String DEFAULT_PROMPT = "检测画面中是否存在人员闯入、跌倒、打架、烟火、车辆异常或其他值得告警的事件。";
    private final CameraRepository cameras;
    private final OnvifSoapClient onvif;
    private final CredentialCipher cipher;

    public CameraService(CameraRepository cameras, OnvifSoapClient onvif, CredentialCipher cipher) {
        this.cameras = cameras;
        this.onvif = onvif;
        this.cipher = cipher;
    }

    @Transactional(readOnly = true)
    public List<CameraView> list() {
        return cameras.findAll().stream().map(CameraView::from).toList();
    }

    @Transactional(readOnly = true)
    public Camera get(Long id) {
        return cameras.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "摄像头不存在"));
    }

    @Transactional
    public CameraView upsertDiscovery(DiscoveredDevice device) {
        Camera camera = cameras.findByStableKey(device.stableKey()).orElseGet(Camera::new);
        if (camera.getStableKey() == null) {
            camera.setStableKey(device.stableKey());
            camera.setName(inferName(device));
            camera.setDetectionPrompt(DEFAULT_PROMPT);
        }
        camera.setEndpointUrn(device.endpointUrn());
        camera.setHost(device.host());
        camera.setDeviceServiceUrl(device.deviceServiceUrl());
        camera.setLastSeenAt(Instant.now());
        return CameraView.from(cameras.save(camera));
    }

    @Transactional
    public CameraView configure(Long id, String name, String username, String password) {
        Camera camera = get(id);
        if (name != null && !name.isBlank()) camera.setName(name.trim());
        if (camera.getUsername() == null || (username != null && !username.isBlank())) {
            camera.setUsername(username == null ? "" : username.trim());
        }
        if (password != null && !password.isBlank()) camera.setEncryptedPassword(cipher.encrypt(password));
        return CameraView.from(connect(camera));
    }

    @Transactional
    public CameraView reconnect(Long id) {
        return CameraView.from(connect(get(id)));
    }

    @Transactional
    public CameraView configureDetection(Long id, boolean enabled, String prompt, int intervalSeconds,
                                         BigDecimal confidenceThreshold) {
        Camera camera = get(id);
        if (enabled && (camera.getRtspUri() == null || camera.getRtspUri().isBlank())) {
            throw new ApiException(HttpStatus.CONFLICT, "请先配置摄像头账号并连接成功");
        }
        camera.setDetectionEnabled(enabled);
        camera.setDetectionPrompt(prompt == null || prompt.isBlank() ? DEFAULT_PROMPT : prompt.trim());
        camera.setDetectionIntervalSeconds(Math.max(10, Math.min(3600, intervalSeconds)));
        camera.setConfidenceThreshold(confidenceThreshold.max(BigDecimal.ZERO).min(BigDecimal.ONE));
        return CameraView.from(cameras.save(camera));
    }

    @Transactional
    public void markOffline(Long id) {
        cameras.findById(id).ifPresent(camera -> {
            camera.setOnline(false);
            cameras.save(camera);
        });
    }

    public OnvifSoapClient.Credentials credentials(Camera camera) {
        return new OnvifSoapClient.Credentials(camera.getUsername(), cipher.decrypt(camera.getEncryptedPassword()));
    }

    private Camera connect(Camera camera) {
        try {
            OnvifSoapClient.Credentials credentials = credentials(camera);
            OnvifSoapClient.DeviceInformation device = onvif.getDeviceInformation(camera.getDeviceServiceUrl(), credentials);
            OnvifSoapClient.Capabilities capabilities = onvif.getCapabilities(camera.getDeviceServiceUrl(), credentials);
            if (capabilities.mediaServiceUrl() == null) throw new OnvifException("设备未公布 ONVIF Media 服务");
            OnvifSoapClient.MediaProfile profile = onvif.getPrimaryProfile(capabilities.mediaServiceUrl(), credentials);
            String streamUri = onvif.getStreamUri(capabilities.mediaServiceUrl(), profile.token(), credentials);
            camera.setManufacturer(device.manufacturer());
            camera.setModel(device.model());
            camera.setFirmwareVersion(device.firmwareVersion());
            camera.setSerialNumber(device.serialNumber());
            camera.setMediaServiceUrl(capabilities.mediaServiceUrl());
            camera.setPtzServiceUrl(capabilities.ptzServiceUrl());
            camera.setProfileToken(profile.token());
            camera.setRtspUri(streamUri);
            camera.setPtzSupported(capabilities.ptzServiceUrl() != null && profile.hasPtzConfiguration());
            camera.setOnline(true);
            camera.setLastConnectedAt(Instant.now());
            return cameras.save(camera);
        } catch (OnvifException exception) {
            camera.setOnline(false);
            cameras.save(camera);
            throw new ApiException(HttpStatus.BAD_GATEWAY, exception.getMessage());
        }
    }

    private String inferName(DiscoveredDevice device) {
        for (String scope : device.scopes()) {
            String marker = "onvif://www.onvif.org/name/";
            if (scope.startsWith(marker)) return decode(scope.substring(marker.length()));
        }
        return "ONVIF · " + device.host();
    }

    private String decode(String value) {
        try {
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return value;
        }
    }
}
