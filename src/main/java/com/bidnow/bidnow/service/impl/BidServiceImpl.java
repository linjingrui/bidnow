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

    /** Redis 结束时间 key 前缀 */
    private static final String ENDTIME_KEY_PREFIX = "item:endtime:";

    /** 截杀窗口：最后 N 秒内出价触发延长 */
    private static final int SNIPE_WINDOW_SEC = 30;

    /** 每次延长秒数 */
    private static final int EXTENSION_SEC = 30;

    /** Lua 脚本对象，启动时加载一次，后续复用 */
    private static final DefaultRedisScript<Long> BID_SCRIPT;

    static {
        BID_SCRIPT = new DefaultRedisScript<>();
        BID_SCRIPT.setLocation(new ClassPathResource("lua/bid.lua"));
        BID_SCRIPT.setResultType(Long.class);
    }

    /**
     * 出价流程：
     *   1. 执行 Lua 脚本（原子校验 + 更新 Redis 当前价 + 防截杀延长）
     *   2. Lua 返回 -1 → 拍卖已结束
     *   3. Lua 返回  0 → 价格被超越
     *   4. Lua 返回  1 → 出价成功
     *   5. Lua 返回  2 → 出价成功 + 结束时间已延长
     *   6. 成功时发 MQ，异步写 MySQL
     */
    @Override
    public String bid(Long itemId, Long userId, BigDecimal amount) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BizException(404, "拍品不存在");
        }
        if (!"ACTIVE".equals(item.getStatus())) {
            throw new BizException("拍品未上架，无法出价");
        }

        String priceKey = PRICE_KEY_PREFIX + itemId;
        String endTimeKey = ENDTIME_KEY_PREFIX + itemId;
        long now = System.currentTimeMillis();

        // 执行 Lua：KEYS=[价格key, 结束时间key]
        //           ARGV=[出价金额, 当前毫秒, 截杀窗口秒, 延长时间秒]
        Long result = stringRedisTemplate.execute(
                BID_SCRIPT,
                List.of(priceKey, endTimeKey),
                amount.toString(),
                String.valueOf(now),
                String.valueOf(SNIPE_WINDOW_SEC),
                String.valueOf(EXTENSION_SEC)
        );

        if (result == null) {
            throw new BizException("系统繁忙，请稍后再试");
        }

        if (result == -1) {
            throw new BizException("拍卖已结束");
        }

        if (result == 0) {
            String current = stringRedisTemplate.opsForValue().get(priceKey);
            throw new BizException("出价失败，当前价已更新为 " + current);
        }

        // result == 1 或 2（出价成功）
        // 读 Redis 里最新的 endTime（可能刚被 Lua 延长），一起发给 MQ
        String endTimeStr = stringRedisTemplate.opsForValue().get(endTimeKey);
        String msg = itemId + ":" + amount + ":" + userId + ":" + (endTimeStr != null ? endTimeStr : "0");
        rocketMQTemplate.convertAndSend("bid-success", msg);

        if (result == 2) {
            log.info("出价成功+时间延长：用户{} 对拍品{} 出价 {}", userId, itemId, amount);
            return "出价成功，拍卖结束时间已自动延长";
        }

        log.info("出价成功：用户{} 对拍品{} 出价 {}", userId, itemId, amount);
        return "出价成功";
    }
}
