package com.acme.cms.camera.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class CameraSaveReq {
    @NotBlank
    private String name;
    @NotBlank
    private String ip;
    private Integer onvifPort = 80;
    private String username;
    private String password;
    private Boolean enabled = true;
}
