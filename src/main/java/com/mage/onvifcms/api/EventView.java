package com.mage.onvifcms.api;

import com.mage.onvifcms.domain.DetectionEvent;

import java.math.BigDecimal;
import java.time.Instant;

public record EventView(Long id, Long cameraId, String cameraName, Instant occurredAt,
                        String eventType, String severity, BigDecimal confidence, String summary) {
    public static EventView from(DetectionEvent event) {
        return new EventView(event.getId(), event.getCamera().getId(), event.getCamera().getName(),
                event.getOccurredAt(), event.getEventType(), event.getSeverity(), event.getConfidence(), event.getSummary());
    }
}

