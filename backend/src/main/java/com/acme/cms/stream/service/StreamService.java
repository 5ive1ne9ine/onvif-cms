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

import java.util.ArrayList;
import java.util.List;
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

    /** cameraId -> proxyKey (addStreamProxy 拉流的 key) */
    private final ConcurrentMap<Long, String> proxyKeys = new ConcurrentHashMap<>();
    /** cameraId -> 转码 ffmpeg 进程 (H265 主码流转 H264 供 WebRTC 播放) */
    private final ConcurrentMap<Long, Process> transcoders = new ConcurrentHashMap<>();

    /**
     * ZLM 容器名, 转码 ffmpeg 通过 docker exec 在该容器内运行 (镜像自带 ffmpeg)。
     * 可通过环境变量 ZLM_CONTAINER 覆盖。
     */
    private static final String ZLM_CONTAINER =
            System.getenv().getOrDefault("ZLM_CONTAINER", "onvif-cms-zlm");
    /** 转码目标分辨率 (高度), 0 表示保持原分辨率。 */
    private static final String TRANSCODE_HEIGHT =
            System.getenv().getOrDefault("CMS_TRANSCODE_HEIGHT", "720");
    /** 转码目标码率 (kbit/s), 0 表示不限制。限制码率可避免高分辨率下 Docker UDP 转发丢包。 */
    private static final String TRANSCODE_BITRATE =
            System.getenv().getOrDefault("CMS_TRANSCODE_BITRATE", "1000");

    public StreamInfo ensureProxy(Long cameraId, boolean enableMp4) {
        Camera cam = cameraService.get(cameraId);
        String main = cam.getMainRtspUrl();
        String sub = cam.getSubRtspUrl();
        if ((main == null || main.isEmpty()) && (sub == null || sub.isEmpty())) {
            throw new BizException(400, "Camera RTSP URL is not available; run probe first");
        }
        String app = zlmProps.getDefaultApp();
        String stream = "cam_" + cam.getId();

        if (!zlmClient.isStreamAlive(app, stream)) {
            // 先尝试用主码流 + ffmpeg 转码 (H265->H264), 这样能保留高清分辨率且兼容 WebRTC。
            // 转码失败 (如机器无 docker/ffmpeg, 或主码流本身就是 H264) 则回退到子码流直拉。
            boolean ok = false;
            if (main != null && !main.isEmpty()) {
                ok = startTranscoder(cam.getId(), main, app, stream);
            }
            if (!ok) {
                String rtsp = (sub != null && !sub.isEmpty()) ? sub : main;
                String key = zlmClient.addStreamProxy(app, stream, rtsp, enableMp4);
                if (key != null) proxyKeys.put(cam.getId(), key);
            }
            waitForStreamAlive(app, stream, 20_000);
        }

        StreamInfo info = new StreamInfo();
        info.setCameraId(cam.getId());
        info.setApp(app);
        info.setStream(stream);
        info.setWebrtcSignalUrl("/api/stream/" + cam.getId() + "/webrtc/offer");
        info.setRtsp("rtsp://" + zlmProps.getWebrtc().getExternIp() + ":554/" + app + "/" + stream);
        return info;
    }

    /**
     * 启动 ffmpeg 转码: 拉取主码流 (可能为 H265), 转码为 H264, 以 RTSP push 推回 ZLM,
     * 形成 WebRTC 可播放的 H264 流。成功返回 true。
     */
    private boolean startTranscoder(Long cameraId, String mainRtsp, String app, String stream) {
        stopTranscoder(cameraId);
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("exec");
        cmd.add("-i");
        cmd.add(ZLM_CONTAINER);
        cmd.add("ffmpeg");
        cmd.add("-loglevel"); cmd.add("error");
        cmd.add("-rtsp_transport"); cmd.add("tcp");
        cmd.add("-i"); cmd.add(mainRtsp);
        cmd.add("-c:v"); cmd.add("libx264");
        cmd.add("-preset"); cmd.add("ultrafast");
        cmd.add("-tune"); cmd.add("zerolatency");
        cmd.add("-profile:v"); cmd.add("baseline");
        cmd.add("-level"); cmd.add("3.1");
        cmd.add("-g"); cmd.add("30");
        if (!"0".equals(TRANSCODE_HEIGHT)) {
            cmd.add("-vf"); cmd.add("scale=-2:" + TRANSCODE_HEIGHT);
        }
        if (!"0".equals(TRANSCODE_BITRATE)) {
            cmd.add("-b:v"); cmd.add(TRANSCODE_BITRATE + "k");
            cmd.add("-maxrate"); cmd.add(TRANSCODE_BITRATE + "k");
            cmd.add("-bufsize"); cmd.add((Integer.parseInt(TRANSCODE_BITRATE) * 2) + "k");
        }
        cmd.add("-c:a"); cmd.add("aac");
        cmd.add("-f"); cmd.add("rtsp");
        cmd.add("rtsp://127.0.0.1/" + app + "/" + stream);
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(new java.io.File(System.getProperty("java.io.tmpdir"),
                    "cms-transcode-" + cameraId + ".log"));
            Process p = pb.start();
            transcoders.put(cameraId, p);
            log.info("Transcoder started for camera {} -> {}/{} (height={})",
                    cameraId, app, stream, TRANSCODE_HEIGHT);
            return true;
        } catch (Exception e) {
            log.warn("startTranscoder failed for camera {}: {} (will fall back to direct pull)", cameraId, e.getMessage());
            return false;
        }
    }

    private void stopTranscoder(Long cameraId) {
        Process p = transcoders.remove(cameraId);
        if (p != null) {
            p.destroy();
            log.info("Transcoder stopped for camera {}", cameraId);
        }
    }

    /**
     * 轮询等待流在 ZLM 中就绪
     */
    private void waitForStreamAlive(String app, String stream, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (zlmClient.isStreamAlive(app, stream)) {
                return;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.warn("Stream {}/{} not alive after {}ms", app, stream, timeoutMs);
        throw new BizException(503, "Stream not ready, please try again later");
    }

    public StreamInfo ensureProxy(Long cameraId) {
        return ensureProxy(cameraId, false);
    }

    public void stop(Long cameraId) {
        stopTranscoder(cameraId);
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
