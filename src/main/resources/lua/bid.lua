-- 原子出价校验
-- KEYS[1] = item:price:{id}  Redis 里的当前价 key
-- ARGV[1] = 出价金额
-- 返回值：1=出价成功  0=价格已被超越（当前价 ≥ 出价）

local currentPrice = redis.call('GET', KEYS[1])

if currentPrice == false then
    -- 第一次有人对这个拍品出价，直接写入
    redis.call('SET', KEYS[1], ARGV[1])
    return 1
end

local current = tonumber(currentPrice)
local bid = tonumber(ARGV[1])

if bid > current then
    redis.call('SET', KEYS[1], ARGV[1])
    return 1
end

return 0
