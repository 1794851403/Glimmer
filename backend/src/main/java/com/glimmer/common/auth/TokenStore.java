package com.glimmer.common.auth;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 单用户单 Token 白名单：glimmer:user:token:{userId} = token。
 *
 * <p>必须使用 core 实例；Token 丢失会被视为未登录，不能随 cache LRU 淘汰。
 */
@Component
public class TokenStore {

    private static final String KEY_PREFIX = "glimmer:user:token:";

    private final StringRedisTemplate coreRedisTemplate;

    public TokenStore(@Qualifier("coreRedisTemplate") StringRedisTemplate coreRedisTemplate) {
        this.coreRedisTemplate = coreRedisTemplate;
    }

    public void save(Long userId, String token, Duration ttl) {
        coreRedisTemplate.opsForValue().set(key(userId), token, ttl);
    }

    public boolean matches(Long userId, String token) {
        String stored = coreRedisTemplate.opsForValue().get(key(userId));
        return token.equals(stored);
    }

    public void remove(Long userId) {
        coreRedisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
