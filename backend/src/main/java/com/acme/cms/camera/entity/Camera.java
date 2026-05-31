package com.acme.cms.camera.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("camera")
public class Camera {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String ip;
    private Integer onvifPort;
    private String username;
    private String password;          // 存储时加密
    private String manufacturer;
    private String model;
    private String serialNo;
    private String firmware;
    private String mainRtspUrl;
    private String subRtspUrl;
    private Boolean ptzSupported;
    private Boolean eventsSupported;
    private String status;            // ONLINE / OFFLINE / ERROR
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
