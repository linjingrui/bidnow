package com.bidnow.bidnow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidnow.bidnow.common.BizException;
import com.bidnow.bidnow.dto.ItemCreateRequest;
import com.bidnow.bidnow.dto.ItemVO;
import com.bidnow.bidnow.entity.Item;
import com.bidnow.bidnow.mapper.ItemMapper;
import com.bidnow.bidnow.service.ItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {

    private final ItemMapper itemMapper;

    @Override
    public ItemVO create(Long sellerId, ItemCreateRequest request) {
        Item item = new Item();
        BeanUtils.copyProperties(request, item);
        item.setSellerId(sellerId);
        item.setStatus("DRAFT");
        item.setCurrentPrice(request.getStartPrice()); // 初始当前价 = 起拍价
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.insert(item);

        ItemVO vo = new ItemVO();
        BeanUtils.copyProperties(item, vo);
        return vo;
    }

    @Override
    public Page<ItemVO> page(Integer pageNum, Integer pageSize, String category) {
        LambdaQueryWrapper<Item> wrapper = new LambdaQueryWrapper<>();
        if (category != null && !category.isEmpty()) {
            wrapper.eq(Item::getCategory, category);
        }
        wrapper.orderByDesc(Item::getCreatedAt);

        Page<Item> page = new Page<>(pageNum, pageSize);
        Page<Item> itemPage = itemMapper.selectPage(page, wrapper);

        // 转换成VO分页对象
        Page<ItemVO> voPage = new Page<>(pageNum, pageSize, itemPage.getTotal());
        voPage.setRecords(itemPage.getRecords().stream().map(item -> {
            ItemVO vo = new ItemVO();
            BeanUtils.copyProperties(item, vo);
            return vo;
        }).toList());
        return voPage;
    }

    @Override
    public ItemVO getById(Long id) {
        Item item = itemMapper.selectById(id);
        if (item == null) {
            throw new BizException(404, "拍品不存在");
        }
        ItemVO vo = new ItemVO();
        BeanUtils.copyProperties(item, vo);
        return vo;
    }

    @Override
    public ItemVO update(Long id, Long sellerId, ItemCreateRequest request) {
        Item item = itemMapper.selectById(id);
        if (item == null) {
            throw new BizException(404, "拍品不存在");
        }
        if (!item.getSellerId().equals(sellerId)) {
            throw new BizException(403, "只能修改自己的拍品");
        }
        if (!"DRAFT".equals(item.getStatus())) {
            throw new BizException("只有草稿状态的拍品才能修改");
        }

        BeanUtils.copyProperties(request, item);
        item.setId(id);
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(item);

        ItemVO vo = new ItemVO();
        BeanUtils.copyProperties(item, vo);
        return vo;
    }

    @Override
    public void delete(Long id, Long sellerId) {
        Item item = itemMapper.selectById(id);
        if (item == null) {
            throw new BizException(404, "拍品不存在");
        }
        if (!item.getSellerId().equals(sellerId)) {
            throw new BizException(403, "只能删除自己的拍品");
        }
        if (!"DRAFT".equals(item.getStatus())) {
            throw new BizException("只有草稿状态的拍品才能删除");
        }
        itemMapper.deleteById(id);
    }
}
