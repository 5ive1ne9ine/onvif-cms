package com.acme.cms.event.onvif;

import com.acme.cms.camera.entity.Camera;
import com.acme.cms.camera.onvif.OnvifClientFactory;
import com.acme.cms.camera.onvif.OnvifDeviceInfo;
import com.acme.cms.camera.onvif.OnvifSoapClient;
import com.acme.cms.camera.service.CameraService;
import com.acme.cms.config.OnvifProperties;
import com.acme.cms.event.service.EventRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 为每个启用了事件规则的摄像头维护 PullPoint 订阅 + 周期性拉取消息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PullPointSubscriptionManager {

    private final CameraService cameraService;
    private final EventRuleService ruleService;
    private final OnvifClientFactory onvifFactory;
    private final OnvifProperties onvifProps;
    private final EventDispatcher dispatcher;

    @Qualifier("onvifScheduler")
    private final ThreadPoolTaskScheduler scheduler;

    /** cameraId -> SubscriptionEndpoint URL */
    private final ConcurrentMap<Long, String> subscriptionEndpoints = new ConcurrentHashMap<>();
    /** cameraId -> 当前拉取任务 */
    private final ConcurrentMap<Long, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 启动时, 为所有启用规则的摄像头建订阅
        scheduler.schedule(() -> {
            try {
                refreshAll();
            } catch (Exception e) {
                log.warn("Initial refreshAll failed: {}", e.getMessage());
            }
        }, new java.util.Date(System.currentTimeMillis() + 5000));
    }

    @PreDestroy
    public void shutdown() {
        tasks.forEach((id, t) -> t.cancel(false));
        subscriptionEndpoints.forEach((id, endpoint) -> {
            try {
                Camera cam = cameraService.get(id);
                unsubscribe(cam, endpoint);
            } catch (Exception ignore) {}
        });
        tasks.clear();
        subscriptionEndpoints.clear();
    }

    public void refreshAll() {
        cameraService.listEnabled().forEach(c -> ensureForCamera(c.getId()));
    }

    public synchronized void ensureForCamera(Long cameraId) {
        try {
            Camera cam = cameraService.get(cameraId);
            boolean hasRules = !ruleService.listByCamera(cameraId).isEmpty();
            boolean alreadyRunning = tasks.containsKey(cameraId);
            if (!hasRules || !Boolean.TRUE.equals(cam.getEnabled())) {
                if (alreadyRunning) stopForCamera(cameraId);
                return;
            }
            if (alreadyRunning) return;
            startForCamera(cam);
        } catch (Exception e) {
            log.warn("ensureForCamera({}) failed: {}", cameraId, e.getMessage());
        }
    }

    public synchronized void stopForCamera(Long cameraId) {
        ScheduledFuture<?> t = tasks.remove(cameraId);
        if (t != null) t.cancel(false);
        String ep = subscriptionEndpoints.remove(cameraId);
        if (ep != null) {
            try {
                Camera cam = cameraService.get(cameraId);
                unsubscribe(cam, ep);
            } catch (Exception ignore) {}
        }
    }

    private void startForCamera(Camera cam) throws Exception {
        if (!Boolean.TRUE.equals(cam.getEventsSupported())) {
            log.info("Camera {} does not support events, skip", cam.getId());
            return;
        }
        String endpoint = createPullPointSubscription(cam);
        if (endpoint == null) {
            log.warn("Failed to create PullPoint for camera {}", cam.getId());
            return;
        }
        subscriptionEndpoints.put(cam.getId(), endpoint);

        Runnable pullTask = () -> {
            try {
                pullAndDispatch(cam, endpoint);
            } catch (Exception e) {
                log.warn("Pull failed for camera {}: {}", cam.getId(), e.getMessage());
                // 失效时尝试重建
                if (e.getMessage() != null
                        && (e.getMessage().contains("Subscription") || e.getMessage().contains("400"))) {
                    try {
                        stopForCamera(cam.getId());
                        ensureForCamera(cam.getId());
                    } catch (Exception ignore) {}
                }
            }
        };
        ScheduledFuture<?> f = scheduler.scheduleWithFixedDelay(
                pullTask, Duration.ofSeconds(onvifProps.getPullIntervalSeconds()));
        tasks.put(cam.getId(), f);

        // Renew 任务: 比 TTL 提前 15s renew
        int renewInterval = Math.max(15, onvifProps.getSubscriptionTtlSeconds() - 15);
        scheduler.scheduleWithFixedDelay(() -> renew(cam, endpoint),
                Duration.ofSeconds(renewInterval));

        log.info("Started ONVIF event subscription for camera {}", cam.getId());
    }

    private String createPullPointSubscription(Camera cam) throws Exception {
        OnvifDeviceInfo info = onvifFactory.probe(cam);
        if (info.getEventsXAddr() == null) return null;
        String eventsXAddr = OnvifClientFactory.sanitizeXaddr(cam.getIp(), info.getEventsXAddr());
        OnvifSoapClient client = onvifFactory.client(cam, eventsXAddr);

        String body = "<tev:CreatePullPointSubscription>"
                + "<tev:InitialTerminationTime>PT"
                + onvifProps.getSubscriptionTtlSeconds() + "S</tev:InitialTerminationTime>"
                + "</tev:CreatePullPointSubscription>";
        String resp = client.call(
                "http://www.onvif.org/ver10/events/wsdl/EventPortType/CreatePullPointSubscriptionRequest",
                body, true);
        Document d = OnvifSoapClient.parseXml(resp);
        NodeList nl = d.getElementsByTagNameNS("*", "SubscriptionReference");
        if (nl.getLength() == 0) return null;
        Element subRef = (Element) nl.item(0);
        NodeList addr = subRef.getElementsByTagNameNS("*", "Address");
        if (addr.getLength() == 0) return null;
        String endpoint = addr.item(0).getTextContent();
        return OnvifClientFactory.sanitizeXaddr(cam.getIp(), endpoint);
    }

    private void pullAndDispatch(Camera cam, String endpoint) throws Exception {
        OnvifSoapClient client = onvifFactory.client(cam, endpoint);
        String body = "<tev:PullMessages>"
                + "<tev:Timeout>PT5S</tev:Timeout>"
                + "<tev:MessageLimit>100</tev:MessageLimit>"
                + "</tev:PullMessages>";
        String resp = client.call(
                "http://www.onvif.org/ver10/events/wsdl/PullPointSubscription/PullMessagesRequest",
                body, true);
        Document d = OnvifSoapClient.parseXml(resp);
        NodeList msgs = d.getElementsByTagNameNS("*", "NotificationMessage");
        for (int i = 0; i < msgs.getLength(); i++) {
            dispatcher.dispatch(cam, (Element) msgs.item(i));
        }
    }

    private void renew(Camera cam, String endpoint) {
        try {
            OnvifSoapClient client = onvifFactory.client(cam, endpoint);
            String body = "<wsnt:Renew xmlns:wsnt=\"http://docs.oasis-open.org/wsn/b-2\">"
                    + "<wsnt:TerminationTime>PT"
                    + onvifProps.getSubscriptionTtlSeconds() + "S</wsnt:TerminationTime>"
                    + "</wsnt:Renew>";
            client.call("http://docs.oasis-open.org/wsn/bw-2/SubscriptionManager/RenewRequest",
                    body, true);
        } catch (Exception e) {
            log.warn("Renew failed for camera {}: {}", cam.getId(), e.getMessage());
        }
    }

    private void unsubscribe(Camera cam, String endpoint) throws Exception {
        OnvifSoapClient client = onvifFactory.client(cam, endpoint);
        String body = "<wsnt:Unsubscribe xmlns:wsnt=\"http://docs.oasis-open.org/wsn/b-2\"/>";
        client.call("http://docs.oasis-open.org/wsn/bw-2/SubscriptionManager/UnsubscribeRequest",
                body, true);
    }
}
