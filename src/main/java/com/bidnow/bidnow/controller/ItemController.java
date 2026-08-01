package com.bidnow.bidnow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidnow.bidnow.common.Result;
import com.bidnow.bidnow.dto.ItemCreateRequest;
import com.bidnow.bidnow.dto.ItemVO;
import com.bidnow.bidnow.service.ItemService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;
    private final HttpServletRequest request;

    @PostMapping
    public Result<ItemVO> create(@RequestBody ItemCreateRequest req) {
        Long userId = (Long) request.getAttribute("userId");
        ItemVO vo = itemService.create(userId, req);
        return Result.success(vo);
    }

    @GetMapping
    public Result<Page<ItemVO>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        Page<ItemVO> page = itemService.page(pageNum, pageSize, category, keyword);
        return Result.success(page);
    }

    /** 我的拍品列表。放在 /{id} 前面，避免 Spring 把 "my" 当成 id 解析。 */
    @GetMapping("/my")
    public Result<Page<ItemVO>> myItems(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = (Long) request.getAttribute("userId");
        Page<ItemVO> page = itemService.myItems(pageNum, pageSize, userId);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result<ItemVO> getById(@PathVariable Long id) {
        ItemVO vo = itemService.getById(id);
        return Result.success(vo);
    }

    @PutMapping("/{id}")
    public Result<ItemVO> update(@PathVariable Long id, @RequestBody ItemCreateRequest req) {
        Long userId = (Long) request.getAttribute("userId");
        ItemVO vo = itemService.update(id, userId, req);
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        itemService.delete(id, userId);
        return Result.success();
    }

    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        itemService.publish(id, userId);
        return Result.success();
    }

    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        itemService.close(id, userId);
        return Result.success();
    }
}
