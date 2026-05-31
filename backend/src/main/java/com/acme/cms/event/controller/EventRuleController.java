package com.acme.cms.event.controller;

import com.acme.cms.common.R;
import com.acme.cms.event.entity.EventRule;
import com.acme.cms.event.onvif.PullPointSubscriptionManager;
import com.acme.cms.event.service.EventRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/event-rules")
@RequiredArgsConstructor
public class EventRuleController {

    private final EventRuleService ruleService;
    private final PullPointSubscriptionManager subscriptionManager;

    @GetMapping
    public R<List<EventRule>> list(@RequestParam Long cameraId) {
        return R.ok(ruleService.listByCamera(cameraId));
    }

    @PostMapping
    public R<EventRule> create(@RequestBody EventRule rule) {
        EventRule saved = ruleService.create(rule);
        subscriptionManager.ensureForCamera(saved.getCameraId());
        return R.ok(saved);
    }

    @PutMapping("/{id}")
    public R<EventRule> update(@PathVariable Long id, @RequestBody EventRule rule) {
        EventRule saved = ruleService.update(id, rule);
        subscriptionManager.ensureForCamera(saved.getCameraId());
        return R.ok(saved);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        EventRule existing = ruleService.get(id);
        ruleService.delete(id);
        if (existing != null) subscriptionManager.ensureForCamera(existing.getCameraId());
        return R.ok();
    }
}
