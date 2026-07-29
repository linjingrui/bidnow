package com.bidnow.bidnow.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 缓存删除的消费者。
 * 监听 Topic "cache-delete"，收到消息后就删除对应的 Redis key。
 * 这样 update/delete 接口不阻塞，缓存清理异步完成。
 *
 * consumeMode = ORDERLY：按顺序消费，保证消息不丢不跳。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "cache-delete",
        consumerGroup = "cache-delete-consumer",
        consumeMode = org.apache.rocketmq.spring.annotation.ConsumeMode.ORDERLY
)
public class CacheDeleteConsumer implements RocketMQListener<String> {

    private final RedisTemplate<String, Object> redisTemplate;

    /** 处理一条缓存删除消息 */
    @Override
    public void onMessage(String cacheKey) {
        try {
            log.info("MQ消费者：准备删除缓存 key={}", cacheKey);
            Boolean deleted = redisTemplate.delete(cacheKey);
            if (deleted) {
                log.info("MQ消费者：缓存已删除 key={}", cacheKey);
            } else {
                log.info("MQ消费者：key={} 不存在，跳过", cacheKey);
            }
        } catch (Exception e) {
            // 消费异常会被 RocketMQ 自动重试，这里只记录日志
            log.error("MQ消费者：删除缓存失败 key={}, error={}", cacheKey, e.getMessage());
            throw e;  // 抛出去让 MQ 重试
        }
    }
}
