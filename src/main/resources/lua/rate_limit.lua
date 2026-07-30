-- 滑动窗口限流。
-- KEYS[1]: 限流key（如 rate:bid:2）
-- ARGV[1]: 窗口大小（秒）
-- ARGV[2]: 最大请求数
-- ARGV[3]: 当前时间戳（毫秒）
-- ARGV[4]: 过期时间（秒），用于自动清理 key
--
-- 返回 1 = 通过, 0 = 限流拒绝

local key = KEYS[1]
local window = tonumber(ARGV[1])
local maxRequests = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local expire = tonumber(ARGV[4])

-- 1. 删除窗口之外的旧数据
local windowStart = now - window * 1000
redis.call('ZREMRANGEBYSCORE', key, 0, windowStart)

-- 2. 统计窗口内剩余次数
local count = redis.call('ZCARD', key)

if count >= maxRequests then
    return 0  -- 超限，拒绝
end

-- 3. 记录本次请求
redis.call('ZADD', key, now, now)

-- 4. 设置过期时间，避免冷用户数据常驻内存
redis.call('EXPIRE', key, expire)

return 1  -- 通过
