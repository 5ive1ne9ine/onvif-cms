package com.mage.onvifcms.service;

import com.mage.onvifcms.api.CameraView;
import com.mage.onvifcms.onvif.DiscoveredDevice;
import com.mage.onvifcms.onvif.WsDiscoveryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class DiscoveryService {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);
    private final WsDiscoveryClient discoveryClient;
    private final CameraService cameraService;
    private final AtomicBoolean scanning = new AtomicBoolean();

    public DiscoveryService(WsDiscoveryClient discoveryClient, CameraService cameraService) {
        this.discoveryClient = discoveryClient;
        this.cameraService = cameraService;
    }

    @Scheduled(initialDelay = 2500, fixedDelayString = "${app.discovery.interval-seconds:30}000")
    public void scheduledScan() {
        try {
            scan();
        } catch (Exception exception) {
            log.warn("自动搜索 ONVIF 摄像头失败：{}", exception.getMessage());
        }
    }

    public List<CameraView> scan() {
        if (!scanning.compareAndSet(false, true)) return cameraService.list();
        try {
            List<DiscoveredDevice> devices = discoveryClient.discover();
            devices.forEach(cameraService::upsertDiscovery);
            log.info("ONVIF discovery completed: {} device(s)", devices.size());
            return cameraService.list();
        } finally {
            scanning.set(false);
        }
    }
}
