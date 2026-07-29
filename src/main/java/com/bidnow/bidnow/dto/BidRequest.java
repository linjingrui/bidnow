package com.bidnow.bidnow.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 出价请求
 */
@Data
public class BidRequest {

    /** 出价金额 */
    private BigDecimal amount;
}
