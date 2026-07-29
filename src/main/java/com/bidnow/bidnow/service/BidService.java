package com.bidnow.bidnow.service;

import java.math.BigDecimal;

public interface BidService {

    /**
     * 出价。
     * @param itemId  拍品ID
     * @param userId  出价用户ID
     * @param amount  出价金额
     * @return 出价结果描述
     */
    String bid(Long itemId, Long userId, BigDecimal amount);
}
