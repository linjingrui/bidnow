package com.bidnow.bidnow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidnow.bidnow.dto.NotificationVO;

public interface NotificationService {

    /**
     * 保存通知到 MySQL 并实时推送给用户（WebSocket）。
     * 推送失败不影响持久化——用户可以通过 REST API 拉取历史通知。
     */
    void saveAndPush(Long userId, String type, Long itemId, String title, String content);

    /** 分页查询通知列表（按时间倒序） */
    Page<NotificationVO> page(Long userId, Integer pageNum, Integer pageSize);

    /** 未读通知数量 */
    Long unreadCount(Long userId);

    /** 标记单条已读 */
    void markRead(Long notificationId, Long userId);

    /** 全部已读 */
    void markAllRead(Long userId);
}
