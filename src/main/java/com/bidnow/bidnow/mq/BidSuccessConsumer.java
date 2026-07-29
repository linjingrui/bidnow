package com.bidnow.bidnow.mq;

import com.bidnow.bidnow.entity.Item;
import com.bidnow.bidnow.mapper.ItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 出价成功的消费者。
 * 监听 "bid-success" Topic，负责更新 MySQL 的 current_price + 删除拍品详情缓存。
 *
 * 消息格式：itemId:newPrice:userId  （例如 "2:6000:1"）
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "bid-success",
        consumerGroup = "bid-success-consumer",
        consumeMode = ConsumeMode.ORDERLY     // 顺序消费，保证同一个拍品的出价按序更新
)
public class BidSuccessConsumer implements RocketMQListener<String> {

    private final ItemMapper itemMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(String message) {
        // 解析消息：itemId:newPrice:userId
        String[] parts = message.split(":");
        Long itemId = Long.valueOf(parts[0]);
        String newPrice = parts[1];
        Long userId = Long.valueOf(parts[2]);

        log.info("BidSuccessConsumer：处理出价结果 itemId={}, newPrice={}, userId={}", itemId, newPrice, userId);

        // 1. 更新 MySQL
        Item item = itemMapper.selectById(itemId);
        if (item != null) {
            item.setCurrentPrice(new java.math.BigDecimal(newPrice));
            itemMapper.updateById(item);
            log.info("BidSuccessConsumer：MySQL已更新 itemId={}, newPrice={}", itemId, newPrice);
        }

        // 2. 删除缓存，下次读的时候重建并拿到最新价格
        redisTemplate.delete("item:" + itemId);
        log.info("BidSuccessConsumer：缓存已删除 itemId={}", itemId);
    }
}
