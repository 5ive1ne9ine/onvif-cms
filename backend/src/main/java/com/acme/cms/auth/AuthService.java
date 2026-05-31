package com.acme.cms.auth;

import com.acme.cms.auth.dto.LoginReq;
import com.acme.cms.auth.dto.LoginResp;
import com.acme.cms.common.BizException;
import com.acme.cms.config.JwtProperties;
import com.acme.cms.user.entity.SystemUser;
import com.acme.cms.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProps;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public LoginResp login(LoginReq req) {
        SystemUser u = userService.findByUsername(req.getUsername());
        if (u == null || Boolean.FALSE.equals(u.getEnabled())) {
            throw new BizException(401, "用户不存在或已禁用");
        }
        if (!encoder.matches(req.getPassword(), u.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        String token = jwtUtil.generate(u.getId(), u.getUsername());
        LoginResp resp = new LoginResp();
        resp.setToken(token);
        resp.setExpiresIn(jwtProps.getExpireMinutes() * 60L);
        resp.setUserId(u.getId());
        resp.setUsername(u.getUsername());
        resp.setNickname(u.getNickname());
        resp.setRole(u.getRole());
        return resp;
    }
}
