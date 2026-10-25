package com.glimmer.user.controller;

import com.glimmer.common.api.Result;
import com.glimmer.user.dto.LoginRequest;
import com.glimmer.user.dto.LoginResponse;
import com.glimmer.user.dto.SmsCodeRequest;
import com.glimmer.user.dto.SmsCodeResponse;
import com.glimmer.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。登录与验证码走白名单，登出由拦截器统一校验登录态。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 发送登录验证码（匿名可调）。
     *
     * <p>验证码写入 Redis 5 分钟；同一手机号限流 5 次/小时，且有 60 秒重发冷却，
     * 冷却期内重复请求返回 42901。local / dev profile 会在响应中带 debugCode，生产环境不返回。
     *
     * @param request 手机号 + 场景（scene 目前只支持 LOGIN）
     * @return 验证码有效期、重发冷却时间；调试环境额外带明文验证码
     */
    @PostMapping("/sms-code")
    public Result<SmsCodeResponse> sendSmsCode(@Valid @RequestBody SmsCodeRequest request) {
        return Result.ok(authService.sendSmsCode(request));
    }

    /**
     * 登录 / 自动注册（匿名可调）。
     *
     * <p>校验并消费短信验证码（成功后立即删除，防重放）；手机号不存在时自动创建账号。
     * 同一手机号限流 10 次/小时。成功后签发 JWT 并写入 Redis 白名单。
     *
     * @param request 手机号 + 6 位验证码
     * @return Bearer Token、有效期（秒）与脱敏后的用户信息
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    /**
     * 登出当前会话（需登录）。
     *
     * <p>删除 Redis 中的 Token 白名单，使当前 Access Token 立即失效；只终结本次会话，
     * 不封禁账号。客户端收到 204 后自行清除本地 Token 即可。
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        authService.logout();
        return ResponseEntity.noContent().build();
    }
}
