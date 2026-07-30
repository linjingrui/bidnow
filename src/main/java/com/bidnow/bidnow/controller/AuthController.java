package com.bidnow.bidnow.controller;

import com.bidnow.bidnow.common.Result;
import com.bidnow.bidnow.dto.LoginRequest;
import com.bidnow.bidnow.dto.RegisterRequest;
import com.bidnow.bidnow.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /** 注册：POST /api/auth/register */
    @PostMapping("/register")
    public Result<Map<String, Long>> register(@RequestBody RegisterRequest request) {
        Long userId = userService.register(request);
        return Result.success(Map.of("userId", userId));
    }

    /** 登录：POST /api/auth/login */
    @PostMapping("/login")
    public Result<Map<String, String>> login(@RequestBody LoginRequest request) {
        String token = userService.login(request);
        return Result.success(Map.of("token", token));
    }
}
