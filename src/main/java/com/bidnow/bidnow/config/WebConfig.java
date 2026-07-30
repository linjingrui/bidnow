package com.bidnow.bidnow.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册登录拦截器。
 * /api/auth/** 放行（注册和登录不需要 token），其余 /api/** 全部拦截。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)   // 用哪个拦截器
                .addPathPatterns("/api/**")          // 拦截哪些路径
                .excludePathPatterns("/api/auth/**"); // 排除哪些路径
    }
}
