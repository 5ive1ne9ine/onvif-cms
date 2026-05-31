package com.acme.cms.auth.dto;

import lombok.Data;

@Data
public class LoginResp {
    private String token;
    private long expiresIn;
    private Long userId;
    private String username;
    private String nickname;
    private String role;
}
