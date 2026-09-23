-- LUA 脚本：批量从 Roaring Bitmap 获取笔记点赞状态

--创建一个 results 数组，用于存储笔记是否被点赞；
--使用 EXISTS 命令检查 Roaring Bitmap 是否存在；
--若不存在，则将数组第一位，设置一个 -1 标识，表示 Roaring Bitmap 不存在，直接 return；
--若存在，循环通过 R.GETBIT 命令，获取点赞状态，依次添加到 results 数组中，返回结果；

local key = KEYS[1] -- 操作的 Redis Key

-- 笔记是否被点赞结果
local results = {}

-- 使用 EXISTS 命令检查 Roaring Bitmap 是否存在
local exists = redis.call('EXISTS', key)
if exists == 0 then
    results[1] = -1  -- 标识 Roaring Bitmap 不存在
    return results
end

-- 循环获取笔记是否点赞，1表示已点赞，0表示未点赞
for i = 1, #ARGV do
    results[i] = redis.call("R.GETBIT", key, ARGV[i])
end

return results
