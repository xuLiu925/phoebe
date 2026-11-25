package com.rebuild.phoebe.web.security;

import com.rebuild.phoebe.common.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 解析 Bearer token，写入 SecurityContext 与 UserContext。
 * 管理员可通过请求头 X-Account-Id 代登录某个广告账户（对应资料 G 中“CRM 代登录”的概念，实现 [I]）。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        try {
            String h = req.getHeader("Authorization");
            if (h != null && h.startsWith("Bearer ")) {
                try {
                    UserContext.CurrentUser u = jwtService.parse(h.substring(7));
                    String acting = req.getHeader("X-Account-Id");
                    if (u.role() == UserContext.Role.ADMIN && acting != null && !acting.isBlank()) {
                        u = new UserContext.CurrentUser(u.userId(), u.username(), u.role(), Long.valueOf(acting.trim()));
                    }
                    UserContext.set(u);
                    var auth = new UsernamePasswordAuthenticationToken(u, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + u.role().name())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (RuntimeException ignored) {
                    // 无效 token：按匿名处理，由安全配置返回 401
                }
            }
            chain.doFilter(req, resp);
        } finally {
            UserContext.clear();
        }
    }
}
