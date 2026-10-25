package com.glimmer.user.service;

import com.glimmer.user.dto.LoginRequest;
import com.glimmer.user.dto.LoginResponse;
import com.glimmer.user.dto.SmsCodeRequest;
import com.glimmer.user.dto.SmsCodeResponse;

/**
 * 认证模块对外服务：验证码、登录、登出。
 *
 * <p>Service 接口是模块对外的事实 Facade，跨模块调用只依赖接口；
 * 具体实现见 {@link com.glimmer.user.service.impl.AuthServiceImpl}。
 */
public interface AuthService {

    /**
     * 生成并缓存登录验证码。
     *
     * <p>短信为 Mock 实现：验证码写入 cache Redis（5 分钟），服务端日志打印；
     * 调用方无需关心发送渠道。
     *
     * @param request 手机号 + 场景
     * @return 验证码有效期与重发冷却时间，调试环境带 debugCode
     */
    SmsCodeResponse sendSmsCode(SmsCodeRequest request);

    /**
     * 校验验证码并登录；手机号不存在时自动注册。
     *
     * <p>验证码校验成功后立即删除；封禁账号拒绝登录。成功后签发 JWT 并写入 Token 白名单。
     *
     * @param request 手机号 + 验证码
     * @return 访问令牌与用户信息
     */
    LoginResponse login(LoginRequest request);

    /**
     * 注销当前登录会话。
     *
     * <p>删除 Redis 中的 Token 白名单；调用前必须已登录（{@code UserContext} 中有 userId）。
     */
    void logout();
}
