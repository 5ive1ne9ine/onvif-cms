package com.mage.onvifcms.service;

import com.mage.onvifcms.api.EventView;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class EventStreamHub {
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("ready").data("connected"));
        } catch (IOException exception) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public void publish(EventView event) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("detection").data(event));
            } catch (Exception exception) {
                emitters.remove(emitter);
                emitter.complete();
            }
        }
    }
}

