package com.bidnow.bidnow.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Redis 缓存数据包装类
 * @param <T> 实际存储的数据类型（比如 ItemVO、List<ItemVO>）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CacheData<T> {

    /** 实际缓存的数据 */
    private T data;

    /** 逻辑过期时间 */
    private LocalDateTime expireTime;
}
