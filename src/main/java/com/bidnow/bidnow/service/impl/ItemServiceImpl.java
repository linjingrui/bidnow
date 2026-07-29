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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {

    private final ItemMapper itemMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    /** 拍品缓存 key 前缀 */
    private static final String CACHE_KEY_PREFIX = "item:";

    /** 正常缓存 TTL：10分钟 */
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    /** 空值缓存 TTL：2分钟，防止缓存穿透 */
    private static final Duration NULL_TTL = Duration.ofMinutes(2);

    /** 空值标记：存一个特殊对象而不是空字符串，避免序列化兼容问题 */
    private static final String NULL_MARKER = "__NULL__";

    /**
     * 创建拍品。
     * request 拷到 entity → 补上前端不传的字段（状态、当前价）→ 插入数据库。
     */
    @Override
    public ItemVO create(Long sellerId, ItemCreateRequest request) {
        Item item = new Item();
        BeanUtils.copyProperties(request, item);
        item.setSellerId(sellerId);
        item.setStatus("DRAFT");                              // 新建拍品默认草稿状态
        item.setCurrentPrice(request.getStartPrice());        // 初始当前价 = 起拍价
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.insert(item);

        ItemVO vo = new ItemVO();
        BeanUtils.copyProperties(item, vo);
        return vo;
    }

    /**
     * 拍品分页列表，按创建时间倒序。
     * 列表数据变化频繁且查询条件多样，不做缓存。
     */
    @Override
    public Page<ItemVO> page(Integer pageNum, Integer pageSize, String category) {
        LambdaQueryWrapper<Item> wrapper = new LambdaQueryWrapper<>();
        if (category != null && !category.isEmpty()) {
            wrapper.eq(Item::getCategory, category);
        }
        wrapper.orderByDesc(Item::getCreatedAt);

        Page<Item> page = new Page<>(pageNum, pageSize);
        Page<Item> itemPage = itemMapper.selectPage(page, wrapper);

        Page<ItemVO> voPage = new Page<>(pageNum, pageSize, itemPage.getTotal());
        voPage.setRecords(itemPage.getRecords().stream().map(item -> {
            ItemVO vo = new ItemVO();
            BeanUtils.copyProperties(item, vo);
            return vo;
        }).toList());
        return voPage;
    }

    /**
     * 拍品详情 —— 缓存优先。
     * 流程：查 Redis → 命中直接返回 → 未命中查 MySQL → 写入 Redis → 返回。
     * MySQL 查不到时写入空字符串，TTL 较短，防止缓存穿透。
     */
    @Override
    public ItemVO getById(Long id) {
        String key = CACHE_KEY_PREFIX + id;

        // 1. 先查 Redis
        Object cached = redisTemplate.opsForValue().get(key);

        if (cached != null) {
            // 2a. 命中空标记（之前查过，不存在） → 直接抛异常，不打 MySQL
            if (NULL_MARKER.equals(cached)) {
                throw new BizException(404, "拍品不存在");
            }
            // 2b. 命中正常缓存 → 直接返回
            return (ItemVO) cached;
        }

        // 3. 缓存未命中 → 查 MySQL
        Item item = itemMapper.selectById(id);
        if (item == null) {
            // 4a. MySQL 也没有 → 写空标记，防止后续请求穿透到 MySQL
            redisTemplate.opsForValue().set(key, NULL_MARKER, NULL_TTL);
            throw new BizException(404, "拍品不存在");
        }

        // 4b. MySQL 有数据 → 写缓存，返回
        ItemVO vo = new ItemVO();
        BeanUtils.copyProperties(item, vo);
        redisTemplate.opsForValue().set(key, vo, CACHE_TTL);
        return vo;
    }

    /**
     * 编辑拍品。
     * 三道防线 → 更新数据库 → 删除缓存（下次查询时自动重建）。
     */
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

        // 数据库已更新 → 删除旧缓存，下次查询自动重建正确的缓存
        redisTemplate.delete(CACHE_KEY_PREFIX + id);

        ItemVO vo = new ItemVO();
        BeanUtils.copyProperties(item, vo);
        return vo;
    }

    /**
     * 删除拍品。
     * 三道防线 → 删除数据库记录 → 删除缓存。
     */
    @Override
    public void delete(Long id, Long sellerId) {
        Item item = itemMapper.selectById(id);
        if (item == null) {
            throw new BizException(404, "拍品不存在");
        }
        if (!item.getSellerId().equals(sellerId)) {
            throw new BizException(403, "只能删除自己的拍品");
        }
        // 只有草稿能删，防止误删正在竞拍中的拍品
        if (!"DRAFT".equals(item.getStatus())) {
            throw new BizException("只有草稿状态的拍品才能删除");
        }
        itemMapper.deleteById(id);

        // 删完数据库同步删缓存
        redisTemplate.delete(CACHE_KEY_PREFIX + id);
    }
}
