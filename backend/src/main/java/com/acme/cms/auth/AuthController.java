package com.acme.cms.auth;

import com.acme.cms.auth.dto.LoginReq;
import com.acme.cms.auth.dto.LoginResp;
import com.acme.cms.common.R;
import com.acme.cms.user.entity.SystemUser;
import com.acme.cms.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/login")
    public R<LoginResp> login(@RequestBody @Valid LoginReq req) {
        return R.ok(authService.login(req));
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        // JWT 无状态, 由前端清除 token
        return R.ok();
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me() {
        Long uid = UserContext.getUserId();
        SystemUser u = userService.findById(uid);
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("nickname", u.getNickname());
        m.put("role", u.getRole());
        return R.ok(m);
    }
}
