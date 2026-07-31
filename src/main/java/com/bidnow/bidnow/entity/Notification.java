package com.bidnow.bidnow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知实体。
 * 每条通知属于一个用户：出价被超 / 拍卖成交 / 流拍 等。
 * 持久化到 MySQL，同时通过 WebSocket 实时推送。
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收通知的用户ID */
    private Long userId;

    /** 通知类型 */
    private String type;

    /** 关联拍品ID（可为null） */
    private Long itemId;

    /** 通知标题 */
    private String title;

    /** 通知正文 */
    private String content;

    /** 已读状态：0=未读 1=已读 */
    private Integer isRead;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
