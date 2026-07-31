package com.bidnow.bidnow.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知视图对象 —— API 返回给前端的格式。
 * 相比实体类：
 * - 隐藏了 userId（API 层面用户只能看自己的通知）
 * - isRead 从 Integer(0/1) 转为 Boolean，前端直接绑定
 */
@Data
public class NotificationVO {

    private Long id;
    private String type;
    private Long itemId;
    private String title;
    private String content;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
