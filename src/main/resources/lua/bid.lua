-- 原子出价校验（含防截杀延长）。
-- KEYS[1] = item:price:{id}   当前价
-- KEYS[2] = item:endtime:{id} 结束时间（毫秒时间戳字符串）
-- ARGV[1] = 出价金额
-- ARGV[2] = 当前时间（毫秒）
-- ARGV[3] = 截杀窗口（秒）——最后 N 秒内出价触发延长
-- ARGV[4] = 延长时间（秒）——触发后结束时间 +N 秒
--
-- 返回值：
--   -1 = 拍卖已结束
--    0 = 价格太低（已被超越）
--    1 = 出价成功（未触发延长）
--    2 = 出价成功（已延长结束时间）

local currentPrice = redis.call('GET', KEYS[1])
local endTimeStr = redis.call('GET', KEYS[2])
local now = tonumber(ARGV[2])

-- ============================================================
-- 1. 检查拍卖是否已结束
-- ============================================================
if endTimeStr ~= false then
    local endTime = tonumber(endTimeStr)
    if now >= endTime then
        return -1
    end
end

-- ============================================================
-- 2. 价格校验
-- ============================================================
if currentPrice == false then
    -- 首次出价，直接写入
    redis.call('SET', KEYS[1], ARGV[1])
    return 1
end

local current = tonumber(currentPrice)
local bid = tonumber(ARGV[1])

if bid <= current then
    return 0
end

-- ============================================================
-- 3. 更新价格
-- ============================================================
redis.call('SET', KEYS[1], ARGV[1])

-- ============================================================
-- 4. 防截杀：最后 snipeWindow 秒内出价 → 延长结束时间
-- ============================================================
if endTimeStr ~= false then
    local endTime = tonumber(endTimeStr)
    local snipeWindow = tonumber(ARGV[3])
    local extension = tonumber(ARGV[4])
    local snipeStart = endTime - snipeWindow * 1000

    if now >= snipeStart then
        local newEndTime = endTime + extension * 1000
        redis.call('SET', KEYS[2], newEndTime)
        return 2  -- 成功 + 时间已延长
    end
end

return 1
