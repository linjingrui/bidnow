package com.bidnow.bidnow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidnow.bidnow.common.BizException;
import com.bidnow.bidnow.common.CacheData;
import com.bidnow.bidnow.dto.ItemCreateRequest;
import com.bidnow.bidnow.dto.ItemVO;
import com.bidnow.bidnow.entity.Item;
import com.bidnow.bidnow.mapper.ItemMapper;
import com.bidnow.bidnow.service.ItemService;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {

    private final ItemMapper itemMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final RocketMQTemplate rocketMQTemplate;

    /** 拍品缓存 key 前缀 */
    private static final String CACHE_KEY_PREFIX = "item:";

    /** 互斥锁 key 前缀 */
    private static final String LOCK_KEY_PREFIX = "lock:item:";

    /** 正常缓存逻辑过期时间 */
    private static final Duration CACHE_DURATION = Duration.ofMinutes(10);

    /** 空值缓存逻辑过期时间（防穿透） */
    private static final Duration NULL_CACHE_DURATION = Duration.ofMinutes(2);

    /** 互斥锁 TTL：防止线程挂了锁永远不释放 */
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);

    /** 缓存为空时休眠重试间隔 */
    private static final long RETRY_SLEEP_MS = 50;

    /** 最大重试次数 */
    private static final int MAX_RETRIES = 3;

    /**
     * 创建拍品。
     */
    @Override
    public ItemVO create(Long sellerId, ItemCreateRequest request) {
        Item item = new Item();
        BeanUtils.copyProperties(request, item);
        item.setSellerId(sellerId);
        item.setStatus("DRAFT");
        item.setCurrentPrice(request.getStartPrice());
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.insert(item);

        // 初始化 Redis 里的当前价（纯数字字符串，供 Lua 脚本 tonumber 解析）
        stringRedisTemplate.opsForValue().set("item:price:" + item.getId(), request.getStartPrice().toString());

        ItemVO vo = new ItemVO();
        BeanUtils.copyProperties(item, vo);
        return vo;
    }

    /**
     * 拍品分页列表。
     */
    @Override
    public Page<ItemVO> page(Integer pageNum, Integer pageSize, String category, String keyword) {
        LambdaQueryWrapper<Item> wrapper = new LambdaQueryWrapper<>();
        // 大厅只展示拍卖中的拍品
        wrapper.eq(Item::getStatus, "ACTIVE");
        if (category != null && !category.isEmpty()) {
            wrapper.eq(Item::getCategory, category);
        }
        // 标题关键字模糊搜索（拍品量级小，LIKE 足够；%kw% 走不了索引，量大要换 ES）
        if (keyword != null && !keyword.isEmpty()) {
            String kw = keyword.trim();
            if (!kw.isEmpty()) {
                wrapper.like(Item::getTitle, kw);
            }
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
     * "我的拍品"列表 —— 按卖家ID筛选。
     */
    @Override
    public Page<ItemVO> myItems(Integer pageNum, Integer pageSize, Long sellerId) {
        LambdaQueryWrapper<Item> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Item::getSellerId, sellerId);
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
     * 拍品详情 —— 逻辑过期 + 互斥锁防缓存击穿。
     *
     * 流程：
     *   缓存命中 + 未过期 → 直接返回
     *   缓存命中 + 逻辑过期 → 抢锁重建（没拿到锁就返回旧数据）
     *   缓存未命中（物理空）→ 抢锁重建（没拿到锁就休眠重试）
     */
    @Override
    public ItemVO getById(Long id) {
        String cacheKey = CACHE_KEY_PREFIX + id;
        String lockKey = LOCK_KEY_PREFIX + id;

        // ========== 分支一：缓存命中 ==========
        CacheData<ItemVO> cacheData = getCachedData(cacheKey);
        if (cacheData != null) {

            // 1a. 数据新鲜，直接返回
            if (LocalDateTime.now().isBefore(cacheData.getExpireTime())) {
                if (cacheData.getData() == null) {
                    throw new BizException(404, "拍品不存在");
                }
                return cacheData.getData();
            }

            // 1b. 逻辑过期 → 尝试抢锁重建
            ItemVO freshData = tryLockAndRebuild(cacheKey, lockKey, id);
            if (freshData != null) {
                return freshData;     // 拿到锁，返回数据库最新数据
            }

            // 没拿到锁 → 返回旧数据（旧的总比没有强）
            if (cacheData.getData() == null) {
                throw new BizException(404, "拍品不存在");
            }
            return cacheData.getData();
        }

        // ========== 分支二：缓存完全空 ==========
        ItemVO freshData = tryLockAndRebuild(cacheKey, lockKey, id);
        if (freshData != null) {
            return freshData;         // 拿到锁，返回数据库最新数据
        }

        // 没拿到锁 → 休眠重试（没有旧数据可返，只能等别人把缓存写好）
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                Thread.sleep(RETRY_SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            cacheData = getCachedData(cacheKey);
            if (cacheData != null) {
                if (cacheData.getData() == null) {
                    throw new BizException(404, "拍品不存在");
                }
                return cacheData.getData();
            }
        }

        // 重试耗尽 → 降级：直接查 MySQL
        Item item = itemMapper.selectById(id);
        if (item == null) {
            throw new BizException(404, "拍品不存在");
        }
        ItemVO vo = new ItemVO();
        BeanUtils.copyProperties(item, vo);
        return vo;
    }

    /**
     * 编辑拍品。
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
        if (!"DRAFT".equals(item.getStatus()) && !"ENDED".equals(item.getStatus())) {
            throw new BizException("只有草稿或已结束的拍品才能修改");
        }

        BeanUtils.copyProperties(request, item);
        item.setId(id);
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(item);

        // 数据库已更新 → 删缓存，下次查询自动重建
        // 异步删除缓存——发消息到 RocketMQ，消费者处理，接口不阻塞
        rocketMQTemplate.convertAndSend("cache-delete", CACHE_KEY_PREFIX + id);

        ItemVO vo = new ItemVO();
        BeanUtils.copyProperties(item, vo);
        return vo;
    }

    /**
     * 删除拍品。
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
        if ("ACTIVE".equals(item.getStatus())) {
            throw new BizException("拍卖中的拍品不能删除，请先下架");
        }
        itemMapper.deleteById(id);
        // 异步删除缓存——发消息到 RocketMQ，消费者处理，接口不阻塞
        rocketMQTemplate.convertAndSend("cache-delete", CACHE_KEY_PREFIX + id);
    }

    /**
     * 上架拍品（DRAFT → ACTIVE）。
     * 同时初始化 Redis 里的结束时间（用于防截杀延长）和自动补齐拍卖时间段。
     */
    @Override
    public void publish(Long id, Long sellerId) {
        Item item = itemMapper.selectById(id);
        if (item == null) {
            throw new BizException(404, "拍品不存在");
        }
        if (!item.getSellerId().equals(sellerId)) {
            throw new BizException(403, "只能上架自己的拍品");
        }
        // DRAFT 首次上架 / ENDED 重新上架
        if (!"DRAFT".equals(item.getStatus()) && !"ENDED".equals(item.getStatus())) {
            throw new BizException("只有草稿或已结束的拍品才能上架");
        }

        // 自动补齐：未设置开始时间 → 立即开始；未设置结束时间（或重新上架）→ 7 天后
        boolean isRePublish = "ENDED".equals(item.getStatus());
        if (item.getStartTime() == null || isRePublish) {
            item.setStartTime(LocalDateTime.now());
        }
        if (item.getEndTime() == null || isRePublish) {
            item.setEndTime(LocalDateTime.now().plusDays(7));
        }

        // 重新上架：重置当前价为起拍价，清理前一次出价数据
        if (isRePublish) {
            item.setCurrentPrice(item.getStartPrice());
            stringRedisTemplate.delete("item:price:" + id);
            stringRedisTemplate.delete("item:proxy:" + id);
        }

        item.setStatus("ACTIVE");
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(item);

        // 把结束时间写入 Redis（毫秒时间戳），供 bid.lua 做防截杀判断
        long endTimeMillis = item.getEndTime()
                .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                .toInstant()
                .toEpochMilli();
        stringRedisTemplate.opsForValue().set("item:endtime:" + id, String.valueOf(endTimeMillis));

        rocketMQTemplate.convertAndSend("cache-delete", CACHE_KEY_PREFIX + id);
    }

    /**
     * 结束拍卖（ACTIVE → ENDED）。
     */
    @Override
    public void close(Long id, Long sellerId) {
        Item item = itemMapper.selectById(id);
        if (item == null) {
            throw new BizException(404, "拍品不存在");
        }
        if (!item.getSellerId().equals(sellerId)) {
            throw new BizException(403, "只能结束自己的拍品");
        }
        if (!"ACTIVE".equals(item.getStatus()) && !"PENDING".equals(item.getStatus())) {
            throw new BizException("当前状态不允许结束拍卖");
        }

        item.setStatus("ENDED");
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(item);

        rocketMQTemplate.convertAndSend("cache-delete", CACHE_KEY_PREFIX + id);
    }

    // ==================== 私有方法 ====================

    /**
     * 从缓存读取 CacheData（做类型检查）。
     */
    @SuppressWarnings("unchecked")
    private CacheData<ItemVO> getCachedData(String key) {
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return (CacheData<ItemVO>) cached;
        }
        return null;
    }

    /**
     * 尝试获取互斥锁并重建缓存。
     * 拿到锁 → 查 MySQL → 写缓存 → 释放锁 → 返回最新数据。
     * 没拿到锁 → 返回 null，由调用方决定下一步（返回旧数据 or 休眠重试）。
     */
    private ItemVO tryLockAndRebuild(String cacheKey, String lockKey, Long id) {
        Boolean gotLock = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOCK_TTL);

        if (!Boolean.TRUE.equals(gotLock)) {
            return null;  // 没抢到锁
        }

        try {
            rebuildCache(id, cacheKey);
            // 重建完成 → 读回最新数据
            CacheData<ItemVO> fresh = getCachedData(cacheKey);
            if (fresh != null) {
                if (fresh.getData() == null) {
                    throw new BizException(404, "拍品不存在");
                }
                return fresh.getData();
            }
            return null;
        } finally {
            // 不管成功失败，必须释放锁
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 缓存重建：查 MySQL → 写入 CacheData。
     * 根据 MySQL 的结果选择正常的逻辑过期时间还是空值短 TTL。
     * 根据 MySQL 的结果选择正常的逻辑过期时间还是空值短 TTL。
     */
    private void rebuildCache(Long id, String cacheKey) {
        Item item = itemMapper.selectById(id);
        if (item == null) {
            // 不存在 → 写空标记（短 TTL，后续请求直接挡在 Redis 层）
            CacheData<ItemVO> nullCache = new CacheData<>(
                    null,
                    LocalDateTime.now().plus(NULL_CACHE_DURATION)
            );
            redisTemplate.opsForValue().set(cacheKey, nullCache, NULL_CACHE_DURATION);
        } else {
            ItemVO vo = new ItemVO();
            BeanUtils.copyProperties(item, vo);
            // 存 CacheData，不设物理 TTL。逻辑过期时间由 expireTime 字段控制
            CacheData<ItemVO> cacheData = new CacheData<>(
                    vo,
                    LocalDateTime.now().plus(CACHE_DURATION)
            );
            redisTemplate.opsForValue().set(cacheKey, cacheData);
        }
    }
}
