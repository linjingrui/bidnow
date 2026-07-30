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

        if (result != null && result == 0) {
            throw new BizException(429, "操作太频繁了，请稍后再试");
        }

        // 通过 → 执行原方法
        return joinPoint.proceed();
    }

    /** 从请求头或 session 里拿当前用户 ID。暂时写死，后续接登录。 */
    private String getCurrentUserId() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                // TODO 后续从 token 解析
                // 临时：支持 header 传 userId 来模拟多用户
                String headerUserId = request.getHeader("X-UserId");
                if (headerUserId != null && !headerUserId.isEmpty()) {
                    return headerUserId;
                }
            }
        } catch (Exception ignored) {
        }
        return "1"; // 默认用户
    }
}
