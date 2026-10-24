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

    SmsCodeResponse sendSmsCode(SmsCodeRequest request);

    LoginResponse login(LoginRequest request);

    void logout();
}
