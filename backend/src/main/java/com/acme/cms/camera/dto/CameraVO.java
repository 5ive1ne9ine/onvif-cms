package com.acme.cms.camera.dto;

import com.acme.cms.camera.entity.Camera;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CameraVO {
    private Long id;
    private String name;
    private String ip;
    private Integer onvifPort;
    private String username;
    private String maskedPassword;       // 永远不回传明文密码
    private String manufacturer;
    private String model;
    private String serialNo;
    private String firmware;
    private String mainRtspUrl;
    private String subRtspUrl;
    private Boolean ptzSupported;
    private Boolean eventsSupported;
    private String status;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CameraVO from(Camera c) {
        CameraVO v = new CameraVO();
        v.setId(c.getId());
        v.setName(c.getName());
        v.setIp(c.getIp());
        v.setOnvifPort(c.getOnvifPort());
        v.setUsername(c.getUsername());
        v.setMaskedPassword(c.getPassword() == null ? null : "******");
        v.setManufacturer(c.getManufacturer());
        v.setModel(c.getModel());
        v.setSerialNo(c.getSerialNo());
        v.setFirmware(c.getFirmware());
        v.setMainRtspUrl(c.getMainRtspUrl());
        v.setSubRtspUrl(c.getSubRtspUrl());
        v.setPtzSupported(c.getPtzSupported());
        v.setEventsSupported(c.getEventsSupported());
        v.setStatus(c.getStatus());
        v.setEnabled(c.getEnabled());
        v.setCreatedAt(c.getCreatedAt());
        v.setUpdatedAt(c.getUpdatedAt());
        return v;
    }
}
