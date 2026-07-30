package com.bidnow.bidnow.service;

import com.bidnow.bidnow.dto.LoginRequest;
import com.bidnow.bidnow.dto.RegisterRequest;

public interface UserService {

    /** 注册，返回新用户 ID */
    Long register(RegisterRequest request);

    /** 登录，返回 JWT 令牌 */
    String login(LoginRequest request);
}
