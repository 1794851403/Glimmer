package com.glimmer.common.ratelimit;

import com.glimmer.common.exception.BizException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.exception.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 滑动窗口限流：ZSet + Lua，成员由应用生成 UUID，避免同一毫秒并发碰撞。
 *
 * <p>限流 Key 放在 cache 实例；该实例不可用时按依赖故障处理，不放行。
 */
@Slf4j
@Component
public class RateLimiter {

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local member = ARGV[4]
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
            local count = redis.call('ZCARD', key)
            if count >= limit then
              local ttl = redis.call('TTL', key)
              if ttl < 0 then ttl = math.ceil(window / 1000) + 1 end
              return ttl
            end
            redis.call('ZADD', key, now, member)
            redis.call('EXPIRE', key, math.ceil(window / 1000) + 1)
            return -1
            """, Long.class);

    private final StringRedisTemplate cacheRedisTemplate;
    private final RateLimitProperties properties;

    public RateLimiter(@Qualifier("cacheRedisTemplate") StringRedisTemplate cacheRedisTemplate,
                       RateLimitProperties properties) {
        this.cacheRedisTemplate = cacheRedisTemplate;
        this.properties = properties;
    }

    /**
     * 校验并记录一次调用；超限时抛出 {@link RateLimitException}。
     */
    public void check(RateLimitAction action, String key) {
        RateLimitProperties.Rule rule = properties.rule(action);
        Long result;
        try {
            result = cacheRedisTemplate.execute(
                    SCRIPT,
                    List.of(action.keyPrefix() + key),
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(rule.window().toMillis()),
                    String.valueOf(rule.limit()),
                    UUID.randomUUID().toString()
            );
        } catch (DataAccessException e) {
            log.error("rate limiter redis unavailable, action={}", action, e);
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "限流服务暂不可用");
        }
        if (result != null && result >= 0) {
            throw new RateLimitException(result, rule.limit());
        }
    }

    public int limit(RateLimitAction action) {
        return properties.rule(action).limit();
    }
}
