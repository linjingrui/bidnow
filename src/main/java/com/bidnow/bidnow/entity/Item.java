package com.bidnow.bidnow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("item")
public class Item {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 卖家ID */
    private Long sellerId;

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

    /** 当前价（最新出价） */
    private BigDecimal currentPrice;

    /** 状态：DRAFT-草稿 / PENDING-待开始 / ACTIVE-竞拍中 / ENDED-已结束 / SOLD-已成交 */
    private String status;

    /** 拍卖类型：ENGLISH-英式拍卖 / DUTCH-荷兰式拍卖 */
    private String auctionType;

    /** 拍卖开始时间 */
    private LocalDateTime startTime;

    /** 拍卖结束时间 */
    private LocalDateTime endTime;

    /** 每次加价幅度 */
    private BigDecimal bidIncrement;

    /** 乐观锁版本号 */
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
