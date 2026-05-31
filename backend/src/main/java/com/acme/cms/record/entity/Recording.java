package com.acme.cms.record.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("recording")
public class Recording {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long cameraId;
    private String type;           // EVENT / MANUAL / SCHEDULED
    private String filePath;
    private Long fileSize;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private String status;         // RECORDING / COMPLETED / FAILED
    private Long eventId;
    private LocalDateTime createdAt;
}
