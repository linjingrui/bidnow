package com.bidnow.bidnow.service;

import java.math.BigDecimal;

public interface BidService {

    /**
     * 出价。
     * @param itemId    拍品ID
     * @param userId    出价用户ID
     * @param amount    出价金额（初次出价额，也可能是代理出价的起始价）
     * @param maxAmount 代理出价上限（null 或等于 amount 表示不使用代理出价）
     * @return 出价结果描述
     */
    String bid(Long itemId, Long userId, BigDecimal amount, BigDecimal maxAmount);
}
