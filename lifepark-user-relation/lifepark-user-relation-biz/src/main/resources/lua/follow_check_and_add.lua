-- LUA 脚本：校验并添加关注关系

-- 获取第一个键（通过Redis命令参数传递），这里是存储关注关系的有序集合（ZSet）的Key。
local key = KEYS[1]
--获取第一个value参数，这是要添加的关注对象的用户ID。
local followUserId = ARGV[1]
--获取第二个参数，这里是一个时间戳，通常用于排序关注关系的时间顺序。
local timestamp = ARGV[2]

-- 使用 EXISTS 命令检查 ZSET 是否存在
local exists = redis.call('EXISTS', key)
if exists == 0 then
    return -1
end

-- 校验关注人数是否上限（是否达到 1000）
local size = redis.call('ZCARD', key)
if size >= 1000 then
    return -2
end

-- 校验目标用户是否已经关注
if redis.call('ZSCORE', key, followUserId) then
    return -3
end

-- ZADD 添加关注关系
redis.call('ZADD', key, timestamp, followUserId)
return 0
