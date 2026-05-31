package com.acme.cms.event.onvif;

import com.acme.cms.camera.entity.Camera;
import com.acme.cms.event.entity.EventLog;
import com.acme.cms.event.entity.EventRule;
import com.acme.cms.event.service.EventLogService;
import com.acme.cms.event.service.EventRuleService;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ONVIF NotificationMessage 解析与分发
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventDispatcher {

    private final EventRuleService ruleService;
    private final EventLogService logService;
    private final ApplicationContext appCtx;

    public void dispatch(Camera cam, Element notificationMessage) {
        try {
            String topic = extractTopic(notificationMessage);
            String source = extractSource(notificationMessage);
            JSONObject data = extractData(notificationMessage);

            EventLog evt = new EventLog();
            evt.setCameraId(cam.getId());
            evt.setTopic(topic);
            evt.setSource(source);
            evt.setPayloadJson(data.toJSONString());
            evt.setOccurredAt(LocalDateTime.now());

            List<EventRule> rules = ruleService.matching(cam.getId(), topic);
            if (rules.isEmpty()) {
                return;
            }

            boolean active = isActive(data);

            for (EventRule rule : rules) {
                evt.setId(null);
                evt.setRuleId(rule.getId());
                logService.save(evt);

                if (active) {
                    Object orch = appCtx.getBean("eventRecordingOrchestrator");
                    orch.getClass()
                            .getMethod("onTrigger", Camera.class, EventRule.class, EventLog.class)
                            .invoke(orch, cam, rule, evt);
                }
            }
        } catch (Exception e) {
            log.warn("dispatch failed: {}", e.getMessage());
        }
    }

    private String extractTopic(Element nm) {
        NodeList nl = nm.getElementsByTagNameNS("*", "Topic");
        if (nl.getLength() == 0) return null;
        String t = nl.item(0).getTextContent();
        return t == null ? null : t.trim();
    }

    private String extractSource(Element nm) {
        // 取 ProducerReference -> Address, 或 Message/Source/SimpleItem
        NodeList prod = nm.getElementsByTagNameNS("*", "ProducerReference");
        if (prod.getLength() > 0) {
            NodeList addr = ((Element) prod.item(0)).getElementsByTagNameNS("*", "Address");
            if (addr.getLength() > 0) return addr.item(0).getTextContent();
        }
        NodeList src = nm.getElementsByTagNameNS("*", "Source");
        if (src.getLength() > 0) {
            NodeList si = ((Element) src.item(0)).getElementsByTagNameNS("*", "SimpleItem");
            if (si.getLength() > 0) {
                Element e = (Element) si.item(0);
                return e.getAttribute("Name") + "=" + e.getAttribute("Value");
            }
        }
        return null;
    }

    private JSONObject extractData(Element nm) {
        JSONObject data = new JSONObject();
        NodeList dataNodes = nm.getElementsByTagNameNS("*", "Data");
        for (int i = 0; i < dataNodes.getLength(); i++) {
            Node n = dataNodes.item(i);
            NodeList items = ((Element) n).getElementsByTagNameNS("*", "SimpleItem");
            for (int j = 0; j < items.getLength(); j++) {
                Element it = (Element) items.item(j);
                data.put(it.getAttribute("Name"), it.getAttribute("Value"));
            }
        }
        return data;
    }

    private boolean isActive(JSONObject data) {
        for (String k : data.keySet()) {
            Object v = data.get(k);
            if (v == null) continue;
            String s = String.valueOf(v).toLowerCase();
            if ("true".equals(s) || "active".equals(s) || "1".equals(s)) return true;
        }
        // 没有明显的 active 标记时, 默认视为触发 (兜底)
        return data.isEmpty();
    }
}
