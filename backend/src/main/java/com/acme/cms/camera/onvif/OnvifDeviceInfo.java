package com.acme.cms.camera.onvif;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ONVIF 设备能力 / 信息汇总
 */
@Data
public class OnvifDeviceInfo {

    private String manufacturer;
    private String model;
    private String firmware;
    private String serialNumber;
    private String hardwareId;

    private String mediaXAddr;
    private String ptzXAddr;
    private String eventsXAddr;
    private String imagingXAddr;

    private boolean ptzSupported;
    private boolean eventsSupported;

    private List<Profile> profiles;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Profile {
        private String token;
        private String name;
        private String rtspUrl;
        private String snapshotUrl;
        private String ptzConfigToken;
        private String videoSourceToken;
    }
}
