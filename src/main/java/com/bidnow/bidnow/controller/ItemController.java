package com.bidnow.bidnow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidnow.bidnow.common.Result;
import com.bidnow.bidnow.dto.ItemCreateRequest;
import com.bidnow.bidnow.dto.ItemVO;
import com.bidnow.bidnow.service.ItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @PostMapping
    public Result<ItemVO> create(@RequestBody ItemCreateRequest request) {
        // 暂时写死卖家ID=1，登录功能后续补上
        Long sellerId = 1L;
        ItemVO vo = itemService.create(sellerId, request);
        return Result.success(vo);
    }

    @GetMapping
    public Result<Page<ItemVO>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String category) {
        Page<ItemVO> page = itemService.page(pageNum, pageSize, category);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result<ItemVO> getById(@PathVariable Long id) {
        ItemVO vo = itemService.getById(id);
        return Result.success(vo);
    }

    @PutMapping("/{id}")
    public Result<ItemVO> update(@PathVariable Long id, @RequestBody ItemCreateRequest request) {
        Long sellerId = 1L; // 暂时写死
        ItemVO vo = itemService.update(id, sellerId, request);
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long sellerId = 1L;
        itemService.delete(id, sellerId);
        return Result.success();
    }

    /**
     * 结束拍卖。
     * POST /api/items/{id}/close
     */
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        Long sellerId = 1L;
        itemService.close(id, sellerId);
        return Result.success();
    }
}
