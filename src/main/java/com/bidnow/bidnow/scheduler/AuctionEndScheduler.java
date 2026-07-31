package com.bidnow.bidnow.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidnow.bidnow.entity.Item;
import com.bidnow.bidnow.mapper.ItemMapper;
import com.bidnow.bidnow.service.NotificationService;
import com.bidnow.bidnow.service.ProxyBidResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 拍卖结束扫描器。
 *
 * 每 5 秒扫描一次：找出 end_time 已过但仍为 ACTIVE 的拍品，结束拍卖并通知相关用户。
 *
 * 为什么用轮询而不是 RocketMQ 延迟消息：
 *   MQ 延迟消息可能丢失（broker 宕机/网络抖动），拍卖"永远不会结束"比轮询开销严重得多。
 *   分布式环境加 Redis SET NX 分布式锁即可安全扩展为多实例。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionEndScheduler {

    private final ItemMapper itemMapper;
    private final NotificationService notificationService;
    private final ProxyBidResolver proxyBidResolver;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String PROXY_KEY_PREFIX = "item:proxy:";
    private static final String PRICE_KEY_PREFIX = "item:price:";
    private static final String ENDTIME_KEY_PREFIX = "item:endtime:";

    @Scheduled(fixedRate = 5000)
    public void checkEndedAuctions() {
        // 1. 查询所有已到期但仍在 ACTIVE 状态的拍品
        LambdaQueryWrapper<Item> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Item::getStatus, "ACTIVE")
               .le(Item::getEndTime, LocalDateTime.now());

        List<Item> endedItems = itemMapper.selectList(wrapper);
        if (endedItems.isEmpty()) return;

        log.info("检测到 {} 个已结束的拍卖", endedItems.size());

        for (Item item : endedItems) {
            try {
                endAuction(item);
            } catch (Exception e) {
                log.error("处理拍卖结束失败 itemId={}", item.getId(), e);
            }
        }
    }

    private void endAuction(Item item) {
        Long itemId = item.getId();

        // 2. 从 Redis 查最高出价者（赢家）
        Long winnerUserId = proxyBidResolver.getCurrentWinner(itemId);

        if (winnerUserId != null) {
            // ---- 有人出价：成交 ----
            item.setStatus("SOLD");
            item.setUpdatedAt(LocalDateTime.now());

            // 通知赢家
            notificationService.saveAndPush(
                    winnerUserId, "AUCTION_WON", itemId,
                    "恭喜您赢得拍品",
                    "「" + item.getTitle() + "」已成交，成交价 ¥" + item.getCurrentPrice()
            );

            // 通知卖家（如果卖家不是赢家自己）
            if (!item.getSellerId().equals(winnerUserId)) {
                notificationService.saveAndPush(
                        item.getSellerId(), "ITEM_SOLD", itemId,
                        "您的拍品已成交",
                        "「" + item.getTitle() + "」已以 ¥" + item.getCurrentPrice() + " 成交"
                );
            }

            // 通知其他出价者（输家）
            Map<Object, Object> allBids = stringRedisTemplate.opsForHash()
                    .entries(PROXY_KEY_PREFIX + itemId);
            for (Object key : allBids.keySet()) {
                Long bidderId = Long.valueOf(key.toString());
                if (!bidderId.equals(winnerUserId) && !bidderId.equals(item.getSellerId())) {
                    notificationService.saveAndPush(
                            bidderId, "AUCTION_LOST", itemId,
                            "拍卖已结束",
                            "「" + item.getTitle() + "」竞拍已结束，可惜您未中标"
                    );
                }
            }

        } else {
            // ---- 无人出价：流拍 ----
            item.setStatus("ENDED");
            item.setUpdatedAt(LocalDateTime.now());

            notificationService.saveAndPush(
                    item.getSellerId(), "ITEM_ENDED", itemId,
                    "您的拍品竞拍结束",
                    "「" + item.getTitle() + "」无人出价，已流拍"
            );
        }

        // 3. 更新数据库（乐观锁：version 不匹配则说明别的线程/实例已经处理了）
        int rows = itemMapper.updateById(item);
        if (rows == 0) {
            log.info("拍卖 {} 已被其他线程处理，跳过", itemId);
            return;
        }

        // 4. 清理 Redis 中的出价数据
        stringRedisTemplate.delete(PROXY_KEY_PREFIX + itemId);
        stringRedisTemplate.delete(PRICE_KEY_PREFIX + itemId);
        stringRedisTemplate.delete(ENDTIME_KEY_PREFIX + itemId);

        log.info("拍卖结束处理完成 itemId={}, status={}, winner={}", itemId, item.getStatus(), winnerUserId);
    }
}
