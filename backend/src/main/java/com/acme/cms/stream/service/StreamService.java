package com.acme.cms.stream.service;

import com.acme.cms.camera.entity.Camera;
import com.acme.cms.camera.service.CameraService;
import com.acme.cms.common.BizException;
import com.acme.cms.config.ZlmProperties;
import com.acme.cms.stream.zlm.ZlmClient;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 流媒体管理 - 维护摄像头到 ZLM 代理流的映射
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamService {

    private final CameraService cameraService;
    private final ZlmClient zlmClient;
    private final ZlmProperties zlmProps;

    /** cameraId -> proxyKey */
    private final ConcurrentMap<Long, String> proxyKeys = new ConcurrentHashMap<>();

    public StreamInfo ensureProxy(Long cameraId, boolean enableMp4) {
        Camera cam = cameraService.get(cameraId);
        String rtsp = cam.getMainRtspUrl();
        if (rtsp == null || rtsp.isEmpty()) {
            throw new BizException(400, "Camera RTSP URL is not available; run probe first");
        }
        String app = zlmProps.getDefaultApp();
        String stream = "cam_" + cam.getId();

        if (!zlmClient.isStreamAlive(app, stream)) {
            String key = zlmClient.addStreamProxy(app, stream, rtsp, enableMp4);
            if (key != null) proxyKeys.put(cam.getId(), key);
        }

        StreamInfo info = new StreamInfo();
        info.setCameraId(cam.getId());
        info.setApp(app);
        info.setStream(stream);
        info.setWebrtcSignalUrl("/api/stream/" + cam.getId() + "/webrtc/offer");
        info.setRtsp("rtsp://" + zlmProps.getWebrtc().getExternIp() + ":554/" + app + "/" + stream);
        return info;
    }

    public StreamInfo ensureProxy(Long cameraId) {
        return ensureProxy(cameraId, false);
    }

    public void stop(Long cameraId) {
        String key = proxyKeys.remove(cameraId);
        if (key != null) {
            zlmClient.delStreamProxy(key);
        }
        zlmClient.closeStream(zlmProps.getDefaultApp(), "cam_" + cameraId);
    }

    public String getStreamName(Long cameraId) {
        return "cam_" + cameraId;
    }

    public String getApp() {
        return zlmProps.getDefaultApp();
    }

    public String webrtcSignal(Long cameraId, String sdpOffer) {
        ensureProxy(cameraId, false);
        return zlmClient.webrtcSignal(zlmProps.getDefaultApp(), "cam_" + cameraId, sdpOffer);
    }

    @Data
    public static class StreamInfo {
        private Long cameraId;
        private String app;
        private String stream;
        private String webrtcSignalUrl;
        private String rtsp;
    }
}
