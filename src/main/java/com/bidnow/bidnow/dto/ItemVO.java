package com.bidnow.bidnow.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ItemVO {

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

    private LocalDateTime createdAt;
}
