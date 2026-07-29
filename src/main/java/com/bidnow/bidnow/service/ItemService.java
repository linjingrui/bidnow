package com.bidnow.bidnow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidnow.bidnow.dto.ItemCreateRequest;
import com.bidnow.bidnow.dto.ItemVO;

public interface ItemService {

    ItemVO create(Long sellerId, ItemCreateRequest request);

    Page<ItemVO> page(Integer pageNum, Integer pageSize, String category);

    ItemVO getById(Long id);

    ItemVO update(Long id, Long sellerId, ItemCreateRequest request);

    void delete(Long id, Long sellerId);

    /**
     * 上架拍品（DRAFT → ACTIVE）。
     */
    void publish(Long id, Long sellerId);

    /**
     * 结束拍卖（卖家手动关闭或系统到期自动关闭）。
     */
    void close(Long id, Long sellerId);
}
