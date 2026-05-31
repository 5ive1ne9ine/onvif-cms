package com.acme.cms.event.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("event_log")
public class EventLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long cameraId;
    private Long ruleId;
    private String topic;
    private String source;
    private String payloadJson;
    private LocalDateTime occurredAt;
    private Long recordingId;
    private String snapshotPath;
    private LocalDateTime createdAt;
}
