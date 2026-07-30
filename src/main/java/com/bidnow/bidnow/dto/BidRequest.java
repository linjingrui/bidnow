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

    /** 代理出价上限（可选）。不填或等于 amount 表示不使用代理出价 */
    private BigDecimal maxAmount;
}
