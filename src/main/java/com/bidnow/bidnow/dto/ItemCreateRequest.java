package com.bidnow.bidnow.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建/编辑拍品时前端传来的请求体。
 * 不含 sellerId（从登录态获取）、status（系统自动设为DRAFT）、version（内部使用）。
 */
@Data
public class ItemCreateRequest {

    /** 拍品标题 */
    private String title;

    /** 拍品描述 */
    private String description;

    /** 封面图片URL */
    private String imageUrl;

    /** 分类：数码/艺术品/收藏品/其他 */
    private String category;

    /** 起拍价 */
    private BigDecimal startPrice;

    /** 拍卖类型：ENGLISH-英式拍卖 / DUTCH-荷兰式拍卖 */
    private String auctionType;

    /** 拍卖开始时间 */
    private LocalDateTime startTime;

    /** 拍卖结束时间 */
    private LocalDateTime endTime;

    /** 每次加价幅度，默认1元 */
    private BigDecimal bidIncrement;
}
