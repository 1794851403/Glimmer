package com.glimmer.common.auth;

import com.glimmer.common.web.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与解析。载荷只放 userId / role / iat / exp，不放手机号等敏感信息。
 */
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        String secret = properties.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET 未配置或长度不足 32 字节；请在 deploy/.env 或环境变量中设置");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(Long userId, String role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.expire());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    public LoginUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long userId = Long.valueOf(claims.getSubject());
        String role = claims.get("role", String.class);
        return new LoginUser(userId, role == null ? "USER" : role);
    }

    public Duration ttl() {
        return properties.expire();
    }
}
