package com.glimmer.common.web;

import com.glimmer.common.exception.BizException;
import com.glimmer.common.exception.ErrorCode;

/**
 * 当前请求的登录用户。由 AuthInterceptor 写入，afterCompletion 必须清理。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return user;
    }

    public static Long userId() {
        return require().userId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
