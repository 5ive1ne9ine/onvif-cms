package com.acme.cms.event.controller;

import com.acme.cms.common.PageResp;
import com.acme.cms.common.R;
import com.acme.cms.event.entity.EventLog;
import com.acme.cms.event.service.EventLogService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventLogController {

    private final EventLogService logService;

    @GetMapping
    public R<PageResp<EventLog>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long cameraId,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        Page<EventLog> p = logService.page(page, size, cameraId, from, to, topic);
        return R.ok(PageResp.of(p.getTotal(), p.getCurrent(), p.getSize(), p.getRecords()));
    }

    @GetMapping("/{id}")
    public R<EventLog> get(@PathVariable Long id) {
        return R.ok(logService.get(id));
    }
}
