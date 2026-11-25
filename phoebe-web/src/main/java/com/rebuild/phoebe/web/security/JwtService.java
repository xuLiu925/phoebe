package com.rebuild.phoebe.web.security;

import com.rebuild.phoebe.common.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/** JWT 签发与解析（新建实现，原项目认证方式 [U]）。 */
@Component
public class JwtService {
    private final SecretKey key;
    private final long expireMillis;

    public JwtService(@Value("${phoebe.jwt.secret}") String secret, @Value("${phoebe.jwt.expire-hours:12}") long hours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = Duration.ofHours(hours).toMillis();
    }

    public String issue(UserContext.CurrentUser u) {
        Date now = new Date();
        var b = Jwts.builder()
                .setSubject(String.valueOf(u.userId()))
                .claim("username", u.username())
                .claim("role", u.role().name())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expireMillis));
        if (u.accountId() != null) {
            b.claim("accountId", u.accountId());
        }
        return b.signWith(key).compact();
    }

    public UserContext.CurrentUser parse(String token) {
        Claims c = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
        Number acc = c.get("accountId", Number.class);
        return new UserContext.CurrentUser(Long.valueOf(c.getSubject()), c.get("username", String.class),
                UserContext.Role.valueOf(c.get("role", String.class)), acc == null ? null : acc.longValue());
    }
}
