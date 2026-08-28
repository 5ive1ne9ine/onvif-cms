package com.mage.onvifcms.service;

import com.mage.onvifcms.api.EventView;
import com.mage.onvifcms.domain.Camera;
import com.mage.onvifcms.domain.DetectionEvent;
import com.mage.onvifcms.mage.MageAnalysis;
import com.mage.onvifcms.mage.MageClient;
import com.mage.onvifcms.repository.DetectionEventRepository;
import com.mage.onvifcms.stream.FfmpegService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Service
public class DetectionAnalysisService {
    private final CameraService cameras;
    private final FfmpegService ffmpeg;
    private final MageClient mage;
    private final DetectionEventRepository events;
    private final EventStreamHub streamHub;

    public DetectionAnalysisService(CameraService cameras, FfmpegService ffmpeg, MageClient mage,
                                    DetectionEventRepository events, EventStreamHub streamHub) {
        this.cameras = cameras;
        this.ffmpeg = ffmpeg;
        this.mage = mage;
        this.events = events;
        this.streamHub = streamHub;
    }

    @Transactional
    public Result analyze(Long cameraId) {
        Camera camera = cameras.get(cameraId);
        MageAnalysis analysis = mage.analyze(ffmpeg.sampleFrames(camera), camera.getDetectionPrompt());
        Optional<EventView> eventView = Optional.empty();
        if (analysis.event() && analysis.confidence() >= camera.getConfidenceThreshold().doubleValue()) {
            DetectionEvent event = new DetectionEvent();
            event.setCamera(camera);
            event.setOccurredAt(Instant.now());
            event.setEventType(analysis.type());
            event.setSeverity(analysis.severity());
            event.setConfidence(BigDecimal.valueOf(analysis.confidence()));
            event.setSummary(limit(analysis.summary(), 1000));
            event.setRawResponse(analysis.rawResponse());
            EventView view = EventView.from(events.saveAndFlush(event));
            streamHub.publish(view);
            eventView = Optional.of(view);
        }
        return new Result(analysis, eventView.orElse(null));
    }

    private String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record Result(MageAnalysis analysis, EventView event) {}
}

