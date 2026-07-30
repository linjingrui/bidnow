package com.bidnow.bidnow.common;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * 限流切面：拦截所有带 @RateLimit 注解的方法。
 * 使用 Redis ZSET 滑动窗口，Lua 保证原子性。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>();

    @PostConstruct
    public void init() {
        SCRIPT.setResultType(Long.class);
        SCRIPT.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/rate_limit.lua")));
    }

    @Around("@annotation(rateLimit)")
    public Object check(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        // 拼 Redis key：prefix + userId（如 rate:bid:2）
        String userId = getCurrentUserId();
        String key = rateLimit.prefix() + userId;

        long now = System.currentTimeMillis();
        int expire = rateLimit.window() * 2; // key 过期时间设为窗口的2倍，兜底

        Long result = stringRedisTemplate.execute(
                SCRIPT,
                List.of(key),
                String.valueOf(rateLimit.window()),
                String.valueOf(rateLimit.maxRequests()),
                String.valueOf(now),
                String.valueOf(expire)
        );

        if (result == 0) {
            throw new BizException(429, "操作太频繁了，请稍后再试");
        }

        // 通过 → 执行原方法
        return joinPoint.proceed();
    }

    /** 从 request 里拿当前用户 ID（LoginInterceptor 解析 JWT 后写入的）。 */
    private String getCurrentUserId() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                Long userId = (Long) request.getAttribute("userId");
                if (userId != null) {
                    return userId.toString();
                }
            }
        } catch (Exception ignored) {
        }
        return "0"; // 未登录用户，兜底
    }
}
