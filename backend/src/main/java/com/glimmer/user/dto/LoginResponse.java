package com.glimmer.user.dto;

/**
 * 登录响应：令牌 + 用户信息。
 */
public record LoginResponse(String tokenType, String accessToken, long expiresIn, UserProfile user) {
}
