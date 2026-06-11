package com.acme.cms.record.service;

import com.acme.cms.camera.entity.Camera;
import com.acme.cms.config.StorageProperties;
import com.acme.cms.event.entity.EventLog;
import com.acme.cms.event.entity.EventRule;
import com.acme.cms.event.service.EventLogService;
import com.acme.cms.record.entity.Recording;
import com.acme.cms.stream.zlm.ZlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 事件触发录制 + 抓图编排
 *
 * 实现策略:
 *   1. 抓图: 立即调用 ZLM getSnap (使用拉好的代理流), 异步保存
 *   2. 录制: 启动 FFmpeg 录制 (preSeconds + postSeconds), 用 ZLM 已建立的 rtmp/rtsp 流
 *      避免重复拉流; 若 ZLM 流不在则回退到摄像头直拉
 *      注意: 这是 "事件后" 录制, 没有真正的 "预录" - 真正预录需要 ZLM 分段录制功能
 */
@Slf4j
@Component("eventRecordingOrchestrator")
@RequiredArgsConstructor
public class EventRecordingOrchestrator {

    private final RecordingService recordingService;
    private final EventLogService eventLogService;
    private final ZlmClient zlmClient;
    private final StorageProperties storageProps;
    private final com.acme.cms.stream.service.StreamService streamService;
    private final com.acme.cms.config.ZlmProperties zlmProps;

    @Qualifier("onvifScheduler")
    private final ThreadPoolTaskScheduler scheduler;

    public void onTrigger(Camera cam, EventRule rule, EventLog evt) {
        // 抓图 - 立即
        if (Boolean.TRUE.equals(rule.getSnapshot())) {
            scheduler.schedule(() -> doSnapshot(cam, evt), new Date());
        }
        // 录制 - 立即开始 (postSeconds 长度)
        if (Boolean.TRUE.equals(rule.getRecordVideo())) {
            int duration = (rule.getPreSeconds() == null ? 5 : rule.getPreSeconds())
                    + (rule.getPostSeconds() == null ? 15 : rule.getPostSeconds());
            scheduler.schedule(() -> doRecord(cam, evt, duration), new Date());
        }
    }

    private void doSnapshot(Camera cam, EventLog evt) {
        try {
            // 确保流已开
            streamService.ensureProxy(cam.getId(), false);
            String rtspForSnap = "rtsp://" + extractHost(cam) + ":554/"
                    + streamService.getApp() + "/" + streamService.getStreamName(cam.getId());
            byte[] jpg = zlmClient.getSnap(rtspForSnap, 5, 5);
            if (jpg == null || jpg.length == 0) {
                log.warn("Snapshot empty for camera {}", cam.getId());
                return;
            }
            Path dir = Paths.get(storageProps.getSnapshotDir(),
                    "cam_" + cam.getId(),
                    LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
            Files.createDirectories(dir);
            String fileName = "evt_" + evt.getId() + "_" + System.currentTimeMillis() + ".jpg";
            Path file = dir.resolve(fileName);
            try (OutputStream out = Files.newOutputStream(file)) {
                out.write(jpg);
            }
            evt.setSnapshotPath(file.toString());
            eventLogService.update(evt);
            log.info("Snapshot saved: {}", file);
        } catch (Exception e) {
            log.warn("Snapshot for event {} failed: {}", evt.getId(), e.getMessage());
        }
    }

    private void doRecord(Camera cam, EventLog evt, int durationSeconds) {
        try {
            streamService.ensureProxy(cam.getId(), false);
            Recording r = recordingService.recordClip(cam, durationSeconds, "EVENT", evt.getId());
            evt.setRecordingId(r.getId());
            eventLogService.update(evt);
        } catch (Exception e) {
            log.warn("Record for event {} failed: {}", evt.getId(), e.getMessage());
        }
    }

    private String extractHost(Camera cam) {
        // 录制取本机 ZLM (因为 ensureProxy 后流已在 ZLM)
        return zlmProps.getWebrtc().getExternIp();
    }
}
