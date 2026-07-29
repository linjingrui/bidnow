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
 *
 * 乐观锁：UPDATE 时 WHERE version=旧值。如果读之后被其他线程改了
 * （拍卖被关闭、价格被另一个出价覆盖），受影响 0 行 → 出价作废。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "bid-success",
        consumerGroup = "bid-success-consumer",
        consumeMode = ConsumeMode.ORDERLY
)
public class BidSuccessConsumer implements RocketMQListener<String> {

    private final ItemMapper itemMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(String message) {
        String[] parts = message.split(":");
        Long itemId = Long.valueOf(parts[0]);
        String newPrice = parts[1];
        Long userId = Long.valueOf(parts[2]);

        log.info("BidSuccessConsumer：处理出价 itemId={}, newPrice={}, userId={}", itemId, newPrice, userId);

        // 1. 读拍品（拿到当前 version）
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            log.warn("BidSuccessConsumer：拍品不存在 itemId={}，跳过", itemId);
            return;
        }

        // 2. 拍卖已结束 → 出价作废
        if ("ENDED".equals(item.getStatus()) || "SOLD".equals(item.getStatus())) {
            log.info("BidSuccessConsumer：拍卖已结束 itemId={}, status={}, 出价作废", itemId, item.getStatus());
            return;
        }

        // 3. 更新价格（乐观锁：WHERE version=读时的值）
        item.setCurrentPrice(new java.math.BigDecimal(newPrice));
        int rows = itemMapper.updateById(item);

        if (rows == 0) {
            // version 不匹配 → 读之后被别人改了（拍卖关闭 或 另一个出价先写入）
            log.info("BidSuccessConsumer：乐观锁冲突 itemId={}, 出价作废", itemId);
            return;
        }

        log.info("BidSuccessConsumer：MySQL已更新 itemId={}, newPrice={}", itemId, newPrice);

        // 4. 删缓存
        redisTemplate.delete("item:" + itemId);
        log.info("BidSuccessConsumer：缓存已删除 itemId={}", itemId);
    }
}
