package com.bidnow.bidnow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidnow.bidnow.common.Result;
import com.bidnow.bidnow.dto.NotificationVO;
import com.bidnow.bidnow.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final HttpServletRequest request;

    /** 分页获取通知列表 */
    @GetMapping
    public Result<Page<NotificationVO>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(notificationService.page(userId, pageNum, pageSize));
    }

    /** 获取未读数量（navbar 铃铛数字） */
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(notificationService.unreadCount(userId));
    }

    /** 标记单条已读 */
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        notificationService.markRead(id, userId);
        return Result.success();
    }

    /** 全部标记已读 */
    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        Long userId = (Long) request.getAttribute("userId");
        notificationService.markAllRead(userId);
        return Result.success();
    }
}
