package com.acme.cms.event.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("event_rule")
public class EventRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long cameraId;
    private String topic;
    private String ruleName;
    private Boolean recordVideo;
    private Boolean snapshot;
    private Integer preSeconds;
    private Integer postSeconds;
    private Boolean enabled;
    private LocalDateTime createdAt;
}
