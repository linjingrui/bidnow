package com.bidnow.bidnow.config;

import com.bidnow.bidnow.common.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器。
 * 每个请求进来先经过这里 —— 验证 JWT，拿出 userId，放进 request。
 */
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {

        // 1. 从 Header 拿 Authorization
        String authHeader = request.getHeader("Authorization");

        // 2. 没有、或者不是 Bearer 开头 → 拒绝
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"请先登录\"}");
            return false;  // false = 不放行，Controller 不会执行
        }

        // 3. 截掉 "Bearer " 前缀（7个字符），拿到纯 token
        String token = authHeader.substring(7);

        // 4. 解析 token，拿出 userId
        Long userId = jwtUtil.getUserId(token);
        if (userId == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"登录已过期，请重新登录\"}");
            return false;
        }

        // 5. 存进 request，Controller 从这取
        request.setAttribute("userId", userId);
        return true;  // 放行
    }
}
