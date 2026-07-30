package com.bidnow.bidnow.controller;

import com.bidnow.bidnow.common.RateLimit;
import com.bidnow.bidnow.common.Result;
import com.bidnow.bidnow.dto.BidRequest;
import com.bidnow.bidnow.service.BidService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;
    private final HttpServletRequest request;

    @RateLimit(window = 1, maxRequests = 3, prefix = "rate:bid:")
    @PostMapping("/{id}/bid")
    public Result<String> bid(@PathVariable Long id, @RequestBody BidRequest req) {
        Long userId = (Long) request.getAttribute("userId");
        String result = bidService.bid(id, userId, req.getAmount(), req.getMaxAmount());
        return Result.success(result);
    }
}
