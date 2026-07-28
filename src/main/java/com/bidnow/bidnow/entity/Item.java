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

    private Long sellerId;

    private String title;

    private String description;

    private String imageUrl;

    private String category;

    private BigDecimal startPrice;

    private BigDecimal currentPrice;

    private String status;

    private String auctionType;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private BigDecimal bidIncrement;

    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
