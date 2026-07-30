package com.bidnow.bidnow.dto;

import lombok.Data;

/**
 * 注册请求体。
 */
@Data
public class RegisterRequest {

    /** 用户名 */
    private String username;

    /** 明文密码（后端用 BCrypt 加密后存库） */
    private String password;

    /** 手机号（可选） */
    private String phone;
}
