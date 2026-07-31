package com.bidnow.bidnow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidnow.bidnow.common.BizException;
import com.bidnow.bidnow.dto.NotificationVO;
import com.bidnow.bidnow.entity.Notification;
import com.bidnow.bidnow.mapper.NotificationMapper;
import com.bidnow.bidnow.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 通知服务实现。
 *
 * 核心链路：
 *   业务事件（出价/拍卖结束）→ saveAndPush()
 *     → notificationMapper.insert()   持久化
 *     → messagingTemplate.convertAndSendToUser()  实时推送
 *
 * WebSocket 推送失败不抛异常——通知已持久化，用户可通过 REST API 获取。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void saveAndPush(Long userId, String type, Long itemId, String title, String content) {
        // 1. 持久化到 MySQL
        Notification notif = new Notification();
        notif.setUserId(userId);
        notif.setType(type);
        notif.setItemId(itemId);
        notif.setTitle(title);
        notif.setContent(content);
        notif.setIsRead(0);
        notif.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notif);

        // 2. 转换为 VO 推送到 WebSocket
        NotificationVO vo = toVO(notif);
        try {
            // convertAndSendToUser = 点对点推送到指定用户的 /queue/notifications
            // Spring 根据 Principal.getName() 匹配 WebSocket session
            messagingTemplate.convertAndSendToUser(
                    userId.toString(), "/queue/notifications", vo);
        } catch (Exception e) {
            // 推送失败不影响持久化：用户不在线或 session 不存在是正常情况
            log.debug("WebSocket 推送失败 userId={}, error={}", userId, e.getMessage());
        }
    }

    @Override
    public Page<NotificationVO> page(Long userId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
               .orderByDesc(Notification::getCreatedAt);

        Page<Notification> entityPage = notificationMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper);

        // 转换 entity → VO
        Page<NotificationVO> voPage = new Page<>(pageNum, pageSize, entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream()
                .map(this::toVO)
                .toList());
        return voPage;
    }

    @Override
    public Long unreadCount(Long userId) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
               .eq(Notification::getIsRead, 0);
        return notificationMapper.selectCount(wrapper);
    }

    @Override
    public void markRead(Long notificationId, Long userId) {
        Notification notif = notificationMapper.selectById(notificationId);
        if (notif == null) {
            throw new BizException(404, "通知不存在");
        }
        // 校验归属：防止用户 A 标记用户 B 的通知
        if (!notif.getUserId().equals(userId)) {
            throw new BizException(403, "无权操作");
        }
        notif.setIsRead(1);
        notificationMapper.updateById(notif);
    }

    @Override
    public void markAllRead(Long userId) {
        // 单条 SQL 批量更新，不开事务/不逐行 update
        LambdaUpdateWrapper<Notification> wrapper = new LambdaUpdateWrapper<>();
        wrapper.set(Notification::getIsRead, 1)
               .eq(Notification::getUserId, userId)
               .eq(Notification::getIsRead, 0);
        notificationMapper.update(null, wrapper);
    }

    // ---- private helpers ----

    private NotificationVO toVO(Notification entity) {
        NotificationVO vo = new NotificationVO();
        vo.setId(entity.getId());
        vo.setType(entity.getType());
        vo.setItemId(entity.getItemId());
        vo.setTitle(entity.getTitle());
        vo.setContent(entity.getContent());
        vo.setIsRead(entity.getIsRead() != null && entity.getIsRead() == 1);
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
