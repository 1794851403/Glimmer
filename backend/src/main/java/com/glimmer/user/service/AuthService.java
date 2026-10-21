package com.glimmer.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.glimmer.common.auth.JwtService;
import com.glimmer.common.auth.TokenStore;
import com.glimmer.common.exception.BizException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.exception.RateLimitException;
import com.glimmer.common.ratelimit.RateLimitAction;
import com.glimmer.common.ratelimit.RateLimiter;
import com.glimmer.common.web.UserContext;
import com.glimmer.user.dto.LoginRequest;
import com.glimmer.user.dto.LoginResponse;
import com.glimmer.user.dto.SmsCodeRequest;
import com.glimmer.user.dto.SmsCodeResponse;
import com.glimmer.user.dto.UserProfile;
import com.glimmer.user.entity.UserDO;
import com.glimmer.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录 / 自动注册 / 登出。
 *
 * <p>MVP 验证码是 Mock：写入 cache 实例并打印日志；dev / local profile 额外回传 debugCode。
 */
@Slf4j
@Service
public class AuthService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final DefaultRedisScript<Long> CONSUME_CODE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final TokenStore tokenStore;
    private final RateLimiter rateLimiter;
    private final ActiveUserTracker activeUserTracker;
    private final StringRedisTemplate cacheRedisTemplate;
    private final Environment environment;

    public AuthService(UserMapper userMapper,
                       JwtService jwtService,
                       TokenStore tokenStore,
                       RateLimiter rateLimiter,
                       ActiveUserTracker activeUserTracker,
                       @Qualifier("cacheRedisTemplate") StringRedisTemplate cacheRedisTemplate,
                       Environment environment) {
        this.userMapper = userMapper;
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
        this.rateLimiter = rateLimiter;
        this.activeUserTracker = activeUserTracker;
        this.cacheRedisTemplate = cacheRedisTemplate;
        this.environment = environment;
    }

    public SmsCodeResponse sendSmsCode(SmsCodeRequest request) {
        String phone = request.phone();
        rateLimiter.check(RateLimitAction.SMS_CODE, phone);

        Boolean acquired = cacheRedisTemplate.opsForValue()
                .setIfAbsent(resendKey(phone), "1", RESEND_INTERVAL);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new RateLimitException(RESEND_INTERVAL.toSeconds(),
                    rateLimiter.limit(RateLimitAction.SMS_CODE));
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        cacheRedisTemplate.opsForValue().set(codeKey(phone), code, CODE_TTL);
        log.info("mock sms code phone={} code={}", maskPhone(phone), code);

        String debugCode = debugProfile() ? code : null;
        return new SmsCodeResponse((int) CODE_TTL.toSeconds(),
                (int) RESEND_INTERVAL.toSeconds(), debugCode);
    }

    public LoginResponse login(LoginRequest request) {
        String phone = request.phone();
        rateLimiter.check(RateLimitAction.LOGIN, phone);

        Long consumed;
        try {
            consumed = cacheRedisTemplate.execute(
                    CONSUME_CODE, List.of(codeKey(phone)), request.code());
        } catch (DataAccessException e) {
            log.error("consume sms code failed, phone={}", maskPhone(phone), e);
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "验证码服务暂不可用");
        }
        if (consumed == null || consumed == 0L) {
            throw new BizException(ErrorCode.INVALID_SMS_CODE);
        }

        UserDO user = findByPhone(phone);
        if (user == null) {
            user = register(phone);
        }
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new BizException(ErrorCode.ACCOUNT_BANNED);
        }

        String role = user.getRole() != null && user.getRole() == 1 ? "ADMIN" : "USER";
        String token = jwtService.generate(user.getId(), role);
        tokenStore.save(user.getId(), token, jwtService.ttl());
        activeUserTracker.markActive(user.getId());

        return new LoginResponse("Bearer", token, jwtService.ttl().toSeconds(),
                UserProfile.from(user));
    }

    public void logout() {
        tokenStore.remove(UserContext.userId());
    }

    private UserDO findByPhone(String phone) {
        return userMapper.selectOne(Wrappers.<UserDO>lambdaQuery()
                .eq(UserDO::getPhone, phone));
    }

    private UserDO register(String phone) {
        LocalDateTime now = LocalDateTime.now();
        UserDO user = new UserDO();
        user.setPhone(phone);
        user.setNickname("微光用户" + phone.substring(7));
        user.setAvatarUrl("");
        user.setBio("");
        user.setRole(0);
        user.setStatus(0);
        user.setFollowingCount(0);
        user.setFollowerCount(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        try {
            userMapper.insert(user);
            return user;
        } catch (DuplicateKeyException e) {
            // 并发首次登录：另一个请求已创建，回查即可。
            UserDO existing = findByPhone(phone);
            if (existing != null) {
                return existing;
            }
            throw e;
        }
    }

    private boolean debugProfile() {
        return environment.acceptsProfiles(Profiles.of("local", "dev"));
    }

    private String codeKey(String phone) {
        return "glimmer:auth:sms:" + phone;
    }

    private String resendKey(String phone) {
        return "glimmer:auth:sms:resend:" + phone;
    }

    private String maskPhone(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
