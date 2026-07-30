package com.bidnow.bidnow.controller;

import com.bidnow.bidnow.common.RateLimit;
import com.bidnow.bidnow.common.Result;
import com.bidnow.bidnow.dto.BidRequest;
import com.bidnow.bidnow.service.BidService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;

    /**
     * 对拍品出价。
     * POST /api/items/{id}/bid
     */
    @RateLimit(window = 1, maxRequests = 3, prefix = "rate:bid:")
    @PostMapping("/{id}/bid")
    public Result<String> bid(@PathVariable Long id, @RequestBody BidRequest request) {
        // 临时写死 userId=2（出价者），后续接登录功能
        Long userId = 2L;
        String result = bidService.bid(id, userId, request.getAmount(), request.getMaxAmount());
        return Result.success(result);
    }
}
