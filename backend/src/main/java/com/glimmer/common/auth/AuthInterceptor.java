package com.glimmer.common.auth;

import com.glimmer.common.exception.BizException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.web.LoginUser;
import com.glimmer.common.web.UserContext;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 统一登录 / 角色校验。默认拒绝：/api/** 全部要求登录，白名单按「方法 + 路径」精确放行。
 *
 * <p>白名单接口仍会尝试解析 Token，合法时注入 UserContext，便于返回 liked / collected 等个性化字段。
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER = "Bearer ";
    private static final String ADMIN_PREFIX = "/api/v1/admin/";

    private static final List<Rule> WHITELIST = List.of(
            new Rule(HttpMethod.POST, "/api/v1/auth/sms-code"),
            new Rule(HttpMethod.POST, "/api/v1/auth/login"),

            new Rule(HttpMethod.GET, "/api/v1/posts"),
            new Rule(HttpMethod.GET, "/api/v1/posts/{postId}"),
            new Rule(HttpMethod.GET, "/api/v1/posts/{postId}/comments"),
            new Rule(HttpMethod.GET, "/api/v1/comments/{commentId}/replies"),

            new Rule(HttpMethod.GET, "/api/v1/feed/hot"),
            new Rule(HttpMethod.GET, "/api/v1/feed/recommended"),
            new Rule(HttpMethod.GET, "/api/v1/search/posts"),

            new Rule(HttpMethod.GET, "/api/v1/seckill/activities"),
            new Rule(HttpMethod.GET, "/api/v1/seckill/activities/{activityId}"),

            new Rule(HttpMethod.GET, "/api/v1/users/{userId}"),
            new Rule(HttpMethod.GET, "/api/v1/users/{userId}/posts"),
            new Rule(HttpMethod.GET, "/api/v1/users/{userId}/followers"),
            new Rule(HttpMethod.GET, "/api/v1/users/{userId}/following"),

            new Rule(HttpMethod.GET, "/api/v1/tags"),
            new Rule(HttpMethod.GET, "/api/v1/tags/hot")
    );

    private final JwtService jwtService;
    private final TokenStore tokenStore;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AuthInterceptor(JwtService jwtService, TokenStore tokenStore) {
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String path = path(request);
        boolean whitelisted = isWhitelisted(request.getMethod(), path);
        String token = bearerToken(request);

        if (!StringUtils.hasText(token)) {
            if (whitelisted) {
                return true;
            }
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }

        LoginUser user;
        try {
            user = jwtService.parse(token);
        } catch (ExpiredJwtException e) {
            if (whitelisted) {
                return true;
            }
            throw new BizException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            if (whitelisted) {
                return true;
            }
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }

        boolean active;
        try {
            active = tokenStore.matches(user.userId(), token);
        } catch (DataAccessException e) {
            log.error("token store unavailable", e);
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "登录服务暂不可用");
        }
        if (!active) {
            if (whitelisted) {
                return true;
            }
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }

        if (path.startsWith(ADMIN_PREFIX) && !user.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }

        UserContext.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private boolean isWhitelisted(String method, String path) {
        // /users/me 及其子路径必须登录，不能被 /users/{userId} 白名单误放行。
        if (path.equals("/api/v1/users/me") || path.startsWith("/api/v1/users/me/")) {
            return false;
        }
        for (Rule rule : WHITELIST) {
            if (rule.method().matches(method) && pathMatcher.match(rule.pattern(), path)) {
                return true;
            }
        }
        return false;
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER)) {
            return null;
        }
        return header.substring(BEARER.length()).trim();
    }

    private String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return StringUtils.hasText(contextPath) && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length())
                : uri;
    }

    private record Rule(HttpMethod method, String pattern) {
    }
}
