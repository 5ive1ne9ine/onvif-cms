package com.mage.onvifcms.api;

import com.mage.onvifcms.config.AppProperties;
import com.mage.onvifcms.repository.CameraRepository;
import com.mage.onvifcms.repository.DetectionEventRepository;
import com.mage.onvifcms.service.DetectionScheduler;
import com.mage.onvifcms.service.EventStreamHub;
import com.mage.onvifcms.stream.FfmpegService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DetectionController {
    private final DetectionEventRepository events;
    private final DetectionScheduler scheduler;
    private final EventStreamHub streamHub;
    private final CameraRepository cameras;
    private final FfmpegService ffmpeg;
    private final AppProperties properties;

    public DetectionController(DetectionEventRepository events, DetectionScheduler scheduler,
                               EventStreamHub streamHub, CameraRepository cameras,
                               FfmpegService ffmpeg, AppProperties properties) {
        this.events = events;
        this.scheduler = scheduler;
        this.streamHub = streamHub;
        this.cameras = cameras;
        this.ffmpeg = ffmpeg;
        this.properties = properties;
    }

    @GetMapping("/events")
    public List<EventView> events(@RequestParam(name = "limit", defaultValue = "100") int limit) {
        int safeLimit = Math.max(1, Math.min(500, limit));
        return events.findAllByOrderByOccurredAtDesc(PageRequest.of(0, safeLimit)).stream().map(EventView::from).toList();
    }

    @GetMapping(value = "/events/stream", produces = "text/event-stream")
    public SseEmitter eventStream() {
        return streamHub.subscribe();
    }

    @PostMapping("/cameras/{cameraId}/detection/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> run(@PathVariable("cameraId") Long cameraId) {
        scheduler.trigger(cameraId);
        return Map.of("status", "accepted");
    }

    @GetMapping("/detection/status")
    public Map<Long, DetectionScheduler.DetectionStatus> detectionStatus() {
        return scheduler.statuses();
    }

    @GetMapping("/system/status")
    public SystemStatus systemStatus() {
        long total = cameras.count();
        long online = cameras.findAll().stream().filter(camera -> camera.isOnline()).count();
        long enabled = cameras.findAll().stream().filter(camera -> camera.isDetectionEnabled()).count();
        long todayEvents = events.countByOccurredAtAfter(Instant.now().minus(24, ChronoUnit.HOURS));
        return new SystemStatus(total, online, enabled, todayEvents, ffmpeg.available(),
                properties.mage().enabled(), properties.mage().baseUrl(), properties.mage().model());
    }

    public record SystemStatus(long cameras, long online, long detectionEnabled, long eventsLast24Hours,
                               boolean ffmpegAvailable, boolean mageEnabled, String mageBaseUrl, String mageModel) {}
}
