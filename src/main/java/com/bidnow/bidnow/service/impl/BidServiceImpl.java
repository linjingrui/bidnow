package com.bidnow.bidnow.service.impl;

import com.bidnow.bidnow.common.BizException;
import com.bidnow.bidnow.entity.Item;
import com.bidnow.bidnow.mapper.ItemMapper;
import com.bidnow.bidnow.service.BidService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 出价服务 —— 核心逻辑：加载 Lua 脚本，交 Redis 原子执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BidServiceImpl implements BidService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RocketMQTemplate rocketMQTemplate;
    private final ItemMapper itemMapper;

    /** Redis 当前价 key 前缀 */
    private static final String PRICE_KEY_PREFIX = "item:price:";

    /** Lua 脚本对象，启动时加载一次，后续复用 */
    private static final DefaultRedisScript<Long> BID_SCRIPT;

    static {
        BID_SCRIPT = new DefaultRedisScript<>();
        BID_SCRIPT.setLocation(new ClassPathResource("lua/bid.lua"));
        BID_SCRIPT.setResultType(Long.class);
    }

    /**
     * 出价流程：
     *   1. 执行 Lua 脚本（原子校验 + 更新 Redis 里的当前价）
     *   2. Lua 返回 1 → 成功，发 MQ 消息异步更新 MySQL + 删缓存
     *   3. Lua 返回 0 → 失败，价格已被超越
     */
    @Override
    public String bid(Long itemId, Long userId, BigDecimal amount) {
        // 0. 校验拍品状态，只有 ACTIVE 才能出价
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BizException(404, "拍品不存在");
        }
        if (!"ACTIVE".equals(item.getStatus())) {
            throw new BizException("拍品未上架，无法出价");
        }

        String priceKey = PRICE_KEY_PREFIX + itemId;

        // 执行 Lua 脚本：KEYS = [priceKey]，ARGV = [出价金额]
        Long result = stringRedisTemplate.execute(
                BID_SCRIPT,
                List.of(priceKey),
                amount.toString()
        );

        if (result == 0) {
            // 出价被超越，查一下当前价返回给用户
            String current = stringRedisTemplate.opsForValue().get(priceKey);
            throw new BizException("出价失败，当前价已更新为 " + current);
        }

        // 出价成功 → 发 MQ 异步处理：
        //   1. 更新 MySQL 的 current_price
        //   2. 删除拍品详情缓存
        rocketMQTemplate.convertAndSend("bid-success", itemId + ":" + amount + ":" + userId);

        log.info("出价成功：用户{} 对拍品{} 出价 {}", userId, itemId, amount);
        return "出价成功";
    }
}
