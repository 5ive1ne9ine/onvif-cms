package com.acme.cms.event.service;

import com.acme.cms.event.entity.EventRule;
import com.acme.cms.event.mapper.EventRuleMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EventRuleService {

    private final EventRuleMapper mapper;

    public List<EventRule> listByCamera(Long cameraId) {
        return mapper.selectList(new QueryWrapper<EventRule>()
                .eq("camera_id", cameraId).orderByAsc("id"));
    }

    public List<EventRule> matching(Long cameraId, String topic) {
        return mapper.selectList(new QueryWrapper<EventRule>()
                .eq("camera_id", cameraId)
                .eq("enabled", 1)
                .and(w -> w.eq("topic", topic).or().like("topic", topic)));
    }

    public List<EventRule> listAllEnabled() {
        return mapper.selectList(new QueryWrapper<EventRule>().eq("enabled", 1));
    }

    public EventRule create(EventRule r) {
        if (r.getEnabled() == null) r.setEnabled(true);
        if (r.getRecordVideo() == null) r.setRecordVideo(true);
        if (r.getSnapshot() == null) r.setSnapshot(true);
        if (r.getPreSeconds() == null) r.setPreSeconds(5);
        if (r.getPostSeconds() == null) r.setPostSeconds(15);
        mapper.insert(r);
        return r;
    }

    public EventRule update(Long id, EventRule r) {
        r.setId(id);
        mapper.updateById(r);
        return mapper.selectById(id);
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }

    public EventRule get(Long id) {
        return mapper.selectById(id);
    }
}
