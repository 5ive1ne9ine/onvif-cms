package com.acme.cms.auth;

import com.acme.cms.common.R;
import com.acme.cms.config.JwtProperties;
import com.alibaba.fastjson2.JSON;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final JwtProperties props;

    private static final String[] WHITE_LIST = {
            "/api/auth/login",
            "/zlm/hook/",       // ZLM 回调走自己的鉴权 (token query 参数)
    };

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // 非 /api 与 /zlm 路径不拦截 (前端静态资源直接放行)
        if (!uri.startsWith("/api") && !uri.startsWith("/zlm")) return true;
        for (String w : WHITE_LIST) {
            if (uri.startsWith(w)) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(props.getHeader());
        if (header == null || !header.startsWith(props.getPrefix())) {
            writeUnauthorized(response, "Missing token");
            return;
        }
        String token = header.substring(props.getPrefix().length());
        try {
            Claims claims = jwtUtil.parse(token);
            Long uid = claims.get("uid", Long.class);
            String username = claims.get("username", String.class);
            UserContext.set(uid, username);
            chain.doFilter(request, response);
        } catch (Exception e) {
            log.warn("JWT invalid: {}", e.getMessage());
            writeUnauthorized(response, "Invalid token");
        } finally {
            UserContext.clear();
        }
    }

    private void writeUnauthorized(HttpServletResponse resp, String msg) throws IOException {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(JSON.toJSONString(R.fail(401, msg)));
    }
}
