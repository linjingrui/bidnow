package com.bidnow.bidnow.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 返回给前端的拍品视图对象。
 * 相比 Entity 少了 version（乐观锁内部使用，不需要暴露给前端）。
 */
@Data
public class ItemVO {

    /** 拍品ID */
    private Long id;

    /** 卖家ID */
    private Long sellerId;

    /** 拍品标题 */
    private String title;

    /** 拍品描述 */
    private String description;

    /** 封面图片URL */
    private String imageUrl;

    /** 分类 */
    private String category;

    /** 起拍价 */
    private BigDecimal startPrice;

    /** 当前价 */
    private BigDecimal currentPrice;

    /** 状态 */
    private String status;

    /** 拍卖类型 */
    private String auctionType;

    /** 拍卖开始时间 */
    private LocalDateTime startTime;

    /** 拍卖结束时间 */
    private LocalDateTime endTime;

    /** 每次加价幅度 */
    private BigDecimal bidIncrement;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
