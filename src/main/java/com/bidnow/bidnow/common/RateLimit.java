package com.bidnow.bidnow.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis ZSET 滑动窗口的限流注解。
 * 加在 Controller 方法上即可，AOP 切面自动拦截。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 窗口大小（秒），默认 1 秒 */
    int window() default 1;

    /** 窗口内允许的最大请求数 */
    int maxRequests() default 5;

    /** key 前缀，用于区分不同接口 */
    String prefix() default "rate:";
}
