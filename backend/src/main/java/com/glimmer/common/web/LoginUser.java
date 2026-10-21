package com.glimmer.common.web;

/**
 * 已通过 JWT + Redis 白名单校验的登录用户。
 */
public record LoginUser(Long userId, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
