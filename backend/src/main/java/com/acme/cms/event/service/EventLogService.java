package com.acme.cms.event.service;

import com.acme.cms.event.entity.EventLog;
import com.acme.cms.event.mapper.EventLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EventLogService {

    private final EventLogMapper mapper;

    public Page<EventLog> page(int current, int size, Long cameraId,
                                LocalDateTime from, LocalDateTime to, String topic) {
        Page<EventLog> p = new Page<>(current, size);
        QueryWrapper<EventLog> q = new QueryWrapper<>();
        if (cameraId != null) q.eq("camera_id", cameraId);
        if (topic != null && !topic.isEmpty()) q.like("topic", topic);
        if (from != null) q.ge("occurred_at", from);
        if (to != null) q.le("occurred_at", to);
        q.orderByDesc("occurred_at");
        return mapper.selectPage(p, q);
    }

    public EventLog get(Long id) {
        return mapper.selectById(id);
    }

    public void save(EventLog log) {
        if (log.getOccurredAt() == null) log.setOccurredAt(LocalDateTime.now());
        mapper.insert(log);
    }

    public void update(EventLog log) {
        mapper.updateById(log);
    }
}
