package com.bidnow.bidnow.dto;

import lombok.Data;

/**
 * 登录请求体。
 */
@Data
public class LoginRequest {

    /** 用户名 */
    private String username;

    /** 明文密码 */
    private String password;
}
