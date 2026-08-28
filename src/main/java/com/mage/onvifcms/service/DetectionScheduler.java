package com.mage.onvifcms.service;

import com.mage.onvifcms.api.ApiException;
import com.mage.onvifcms.config.AppProperties;
import com.mage.onvifcms.domain.Camera;
import com.mage.onvifcms.repository.CameraRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

@Service
public class DetectionScheduler {
    private static final Logger log = LoggerFactory.getLogger(DetectionScheduler.class);
    private final CameraRepository cameras;
    private final DetectionAnalysisService analysisService;
    private final ExecutorService executor;
    private final Semaphore analysisSlots;
    private final Set<Long> running = ConcurrentHashMap.newKeySet();
    private final Map<Long, DetectionStatus> statuses = new ConcurrentHashMap<>();

    public DetectionScheduler(CameraRepository cameras, DetectionAnalysisService analysisService,
                              ExecutorService executor, AppProperties properties) {
        this.cameras = cameras;
        this.analysisService = analysisService;
        this.executor = executor;
        this.analysisSlots = new Semaphore(Math.max(1, properties.mage().maxConcurrentAnalyses()));
    }

    @Scheduled(initialDelay = 10000, fixedDelay = 5000)
    public void schedule() {
        Instant now = Instant.now();
        for (Camera camera : cameras.findByDetectionEnabledTrueAndOnlineTrue()) {
            DetectionStatus status = statuses.get(camera.getId());
            Instant due = status == null || status.lastRunAt() == null
                    ? Instant.EPOCH : status.lastRunAt().plus(camera.getDetectionIntervalSeconds(), ChronoUnit.SECONDS);
            if (!due.isAfter(now)) {
                try {
                    trigger(camera.getId());
                } catch (ApiException exception) {
                    log.debug("Skipped scheduled analysis for camera {}: {}", camera.getId(), exception.getMessage());
                }
            }
        }
    }

    public void trigger(Long cameraId) {
        Camera camera = cameras.findById(cameraId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "摄像头不存在"));
        if (camera.getRtspUri() == null) throw new ApiException(HttpStatus.CONFLICT, "摄像头尚未配置视频流");
        if (!running.add(cameraId)) throw new ApiException(HttpStatus.CONFLICT, "该摄像头正在分析中");
        if (!analysisSlots.tryAcquire()) {
            running.remove(cameraId);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "视觉模型并发任务已满，请稍后重试");
        }
        statuses.put(cameraId, new DetectionStatus("RUNNING", "正在提取视频帧并调用视觉模型", Instant.now(), null));
        executor.submit(() -> {
            try {
                DetectionAnalysisService.Result result = analysisService.analyze(cameraId);
                statuses.put(cameraId, new DetectionStatus("IDLE", result.analysis().summary(), Instant.now(), null));
            } catch (Exception exception) {
                String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                statuses.put(cameraId, new DetectionStatus("ERROR", message, Instant.now(), message));
                log.warn("Vision model analysis failed for camera {}: {}", cameraId, message);
            } finally {
                running.remove(cameraId);
                analysisSlots.release();
            }
        });
    }

    public Map<Long, DetectionStatus> statuses() {
        return Map.copyOf(statuses);
    }

    public record DetectionStatus(String state, String message, Instant lastRunAt, String error) {}
}
