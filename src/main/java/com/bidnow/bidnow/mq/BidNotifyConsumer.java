package com.bidnow.bidnow.mq;

import com.bidnow.bidnow.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 出价通知消费者。
 * 监听 "bid-notify" Topic，负责：
 *   1. 向出价人发送"出价成功"确认
 *   2. 向被超越的前任最高出价者发送"已被超越"提醒
 *
 * 消息格式（BidServiceImpl 发送）：
 *   itemId:newPrice:winnerUserId:previousWinnerUserId:biddingUserId:endTimeMillis:itemTitle
 *
 * 与 BidSuccessConsumer 并行但独立 —— 通知推送失败不影响 MySQL 价格更新。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "bid-notify",
        consumerGroup = "bid-notify-consumer",
        consumeMode = ConsumeMode.ORDERLY
)
public class BidNotifyConsumer implements RocketMQListener<String> {

    private final NotificationService notificationService;

    @Override
    public void onMessage(String message) {
        // 解析消息（用 limit=7 防止 itemTitle 中含冒号被切散）
        String[] parts = message.split(":", 7);
        if (parts.length < 7) {
            log.warn("BidNotifyConsumer：消息格式异常，跳过 {}", message);
            return;
        }

        Long itemId = Long.valueOf(parts[0]);
        String newPrice = parts[1];
        Long winnerUserId = Long.valueOf(parts[2]);
        Long previousWinnerUserId = Long.valueOf(parts[3]);
        Long biddingUserId = Long.valueOf(parts[4]);
        String itemTitle = parts[6];

        // ---- 通知1：出价确认（给当前发起出价的人）----
        notificationService.saveAndPush(
                biddingUserId, "BID_SUCCESS", itemId,
                "出价已提交",
                "您对「" + itemTitle + "」的出价 ¥" + newPrice + " 已提交"
        );

        // ---- 通知2：被超越（给之前领先的人）----
        // 条件：存在前赢家 / 前赢家不是新赢家（确实被超越了）/ 前赢家也不是当前出价人自己
        if (previousWinnerUserId != 0
                && !previousWinnerUserId.equals(winnerUserId)
                && !previousWinnerUserId.equals(biddingUserId)) {
            notificationService.saveAndPush(
                    previousWinnerUserId, "OUTBID", itemId,
                    "出价已被超越",
                    "「" + itemTitle + "」当前价已更新为 ¥" + newPrice + "，有人出价更高"
            );
        }

        // ---- 通知3：当前出价人被代理出价超越 ----
        // 条件：新赢家不是当前出价人 / 当前出价人也不是前赢家（否则通知2已经覆盖）
        if (!winnerUserId.equals(biddingUserId) && !biddingUserId.equals(previousWinnerUserId)) {
            notificationService.saveAndPush(
                    biddingUserId, "OUTBID", itemId,
                    "出价已被超越",
                    "「" + itemTitle + "」当前价已更新为 ¥" + newPrice + "（代理出价超越）"
            );
        }

        log.info("BidNotifyConsumer：通知已处理 itemId={}, bidder={}, winner={}, prevWinner={}",
                itemId, biddingUserId, winnerUserId, previousWinnerUserId);
    }
}
