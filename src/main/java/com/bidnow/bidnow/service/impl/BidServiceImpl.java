package com.bidnow.bidnow.service.impl;

import com.bidnow.bidnow.common.BizException;
import com.bidnow.bidnow.entity.Item;
import com.bidnow.bidnow.mapper.ItemMapper;
import com.bidnow.bidnow.service.BidService;
import com.bidnow.bidnow.service.ProxyBidResolver;
import com.bidnow.bidnow.service.ProxyBidResolver.ResolveResult;
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
 * 出价服务。
 *
 * 完整流程：
 *   1. Lua 原子校验（价格 + 时间 + 防截杀）
 *   2. Lua 返回 1/2 → 调用 ProxyBidResolver 解析排名
 *   3. 如果价格变了（有人代理追价 → 赢家可能不是当前出价人）
 *   4. 发 MQ（实际赢家 + 实际价格）→ 异步写 MySQL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BidServiceImpl implements BidService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RocketMQTemplate rocketMQTemplate;
    private final ItemMapper itemMapper;
    private final ProxyBidResolver proxyBidResolver;

    private static final String PRICE_KEY_PREFIX = "item:price:";
    private static final String ENDTIME_KEY_PREFIX = "item:endtime:";
    private static final int SNIPE_WINDOW_SEC = 30;
    private static final int EXTENSION_SEC = 30;

    private static final DefaultRedisScript<Long> BID_SCRIPT;

    static {
        BID_SCRIPT = new DefaultRedisScript<>();
        BID_SCRIPT.setLocation(new ClassPathResource("lua/bid.lua"));
        BID_SCRIPT.setResultType(Long.class);
    }

    @Override
    public String bid(Long itemId, Long userId, BigDecimal amount, BigDecimal maxAmount) {
        // 0. 参数兜底：不填 maxAmount → 等同于普通出价，上限 = 出价额
        BigDecimal effectiveMax = (maxAmount != null && maxAmount.compareTo(amount) > 0)
                ? maxAmount : amount;

        // 1. 校验拍品状态
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BizException(404, "拍品不存在");
        }
        if (!"ACTIVE".equals(item.getStatus())) {
            throw new BizException("拍品未上架，无法出价");
        }
        BigDecimal increment = item.getBidIncrement() != null
                ? item.getBidIncrement() : BigDecimal.ONE;

        // 2. Lua 原子校验（价格 + 结束时间 + 防截杀）
        String priceKey = PRICE_KEY_PREFIX + itemId;
        String endTimeKey = ENDTIME_KEY_PREFIX + itemId;
        long now = System.currentTimeMillis();

        Long result = stringRedisTemplate.execute(
                BID_SCRIPT,
                List.of(priceKey, endTimeKey),
                amount.toString(),               // ARGV[1]
                String.valueOf(now),             // ARGV[2]
                String.valueOf(SNIPE_WINDOW_SEC),// ARGV[3]
                String.valueOf(EXTENSION_SEC)    // ARGV[4]
        );

        if (result == -1) {
            throw new BizException("拍卖已结束");
        }
        if (result == 0) {
            String current = stringRedisTemplate.opsForValue().get(priceKey);
            throw new BizException("出价失败，当前价已更新为 " + current);
        }

        // 3. Lua 通过 → 存入代理上限 + 解析排名
        //    注意：赢家可能不是当前出价人！（别人的代理上限更高，系统帮他追了价）
        ResolveResult resolved = proxyBidResolver.resolve(itemId, userId, effectiveMax, increment);

        // 4. 发 MQ —— 用解析后的 实际赢家 + 实际价格，异步更新 MySQL + 删缓存
        String endTimeStr = stringRedisTemplate.opsForValue().get(endTimeKey);
        String msg = itemId + ":" + resolved.winningPrice() + ":" + resolved.winnerUserId()
                + ":" + (endTimeStr != null ? endTimeStr : "0");
        rocketMQTemplate.convertAndSend("bid-success", msg);

        // 5. 构造返回信息
        StringBuilder sb = new StringBuilder();
        if (resolved.winnerUserId().equals(userId)) {
            sb.append("出价成功");
            if (resolved.winningPrice().compareTo(amount) > 0) {
                // 代理出价帮你追到了比初次出价更高的价格
                sb.append("，当前价 ").append(resolved.winningPrice());
            }
        } else {
            // 你出了价，但别人的代理上限盖过了你
            sb.append("出价已被超越，当前价 ").append(resolved.winningPrice());
        }
        if (result == 2) {
            sb.append("，拍卖结束时间已延长");
        }

        log.info("出价结果：用户{} 出价{} 上限{} itemId={}, winner={}, price={}",
                userId, amount, effectiveMax, itemId, resolved.winnerUserId(), resolved.winningPrice());
        return sb.toString();
    }
}
