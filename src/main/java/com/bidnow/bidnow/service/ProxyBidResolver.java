package com.bidnow.bidnow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 代理出价解析器。
 *
 * 核心算法：
 *   1. 所有出价存入 Redis Hash：item:proxy:{itemId} → {userId: maxAmount}
 *   2. 按 maxAmount 降序排列，取前两名
 *   3. 当前价 = min(第二名上限 + 加价幅度, 第一名上限)
 *
 * 每次有人出价后调用 resolve()，系统自动判断是否需要帮某人追价。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProxyBidResolver {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String PROXY_KEY_PREFIX = "item:proxy:";
    private static final String PRICE_KEY_PREFIX = "item:price:";

    /**
     * 解析结果：谁赢了、以什么价格。
     */
    public record ResolveResult(Long winnerUserId, BigDecimal winningPrice, boolean priceChanged) {}

    /**
     * 存储用户的代理出价上限，然后解析排名。
     *
     * @param itemId     拍品ID
     * @param userId     出价用户
     * @param maxAmount  代理出价上限（普通出价 = amount 即等于 max）
     * @param increment  加价幅度
     * @return 解析结果
     */
    public ResolveResult resolve(Long itemId, Long userId,
                                  BigDecimal maxAmount, BigDecimal increment) {
        // 1. 存：把本次出价记入 Redis Hash
        String proxyKey = PROXY_KEY_PREFIX + itemId;
        stringRedisTemplate.opsForHash().put(proxyKey, userId.toString(), maxAmount.toString());

        // 2. 查：拿出所有用户的代理上限
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(proxyKey);
        if (entries.isEmpty()) {
            return new ResolveResult(userId, maxAmount, false);
        }

        // 3. 排：按上限降序排列，取前两名
        //    每个 entry 是一条 {userId: "200"}，存在 List 里方便排序
        List<Map.Entry<Long, BigDecimal>> bids = new ArrayList<>();
        for (Map.Entry<Object, Object> e : entries.entrySet()) {
            Long uid = Long.valueOf(e.getKey().toString());
            BigDecimal max = new BigDecimal(e.getValue().toString());
            bids.add(new AbstractMap.SimpleEntry<>(uid, max));
        }
        bids.sort((a, b) -> b.getValue().compareTo(a.getValue())); // 降序

        Map.Entry<Long, BigDecimal> top1 = bids.get(0);                            // 第一名
        BigDecimal top2Amount = bids.size() >= 2 ? bids.get(1).getValue() : BigDecimal.ZERO; // 第二名

        // 4. 算：当前价 = min(第二名上限 + 加价幅度, 第一名上限)
        BigDecimal winningPrice = top2Amount.add(increment).min(top1.getValue());
        // 去掉多余小数位
        winningPrice = winningPrice.setScale(2, RoundingMode.HALF_UP);

        String priceKey = PRICE_KEY_PREFIX + itemId;
        String currentPriceStr = stringRedisTemplate.opsForValue().get(priceKey);
        BigDecimal currentPrice = currentPriceStr != null
                ? new BigDecimal(currentPriceStr) : BigDecimal.ZERO;

        boolean priceChanged = winningPrice.compareTo(currentPrice) > 0;

        if (priceChanged) {
            // 5. 写：更新 Redis 当前价
            stringRedisTemplate.opsForValue().set(priceKey, winningPrice.toString());
            log.info("代理出价：itemId={}, winner={}, price={} (top1={}, top2={})",
                    itemId, top1.getKey(), winningPrice, top1.getValue(), top2Amount);
        }

        return new ResolveResult(top1.getKey(), winningPrice, priceChanged);
    }
}
