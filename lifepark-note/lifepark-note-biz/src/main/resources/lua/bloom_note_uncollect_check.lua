--使用 EXISTS 命令校验布隆过滤器是否存在，若不存在，返回 -1 ;
--若存在，使用 BF.EXISTS 命令，校验布隆过滤器中，该篇笔记是否被收藏过，并返回对应值 (1 表示已经收藏，0 表示未收藏) 。

local key = KEYS[1] -- 操作的 Redis Key
local noteId = ARGV[1] -- 笔记ID

-- 使用 EXISTS 命令检查布隆过滤器是否存在
local exists = redis.call('EXISTS', key)
if exists == 0 then
    return -1
end

-- 校验该篇笔记是否被收藏过(1 表示已经收藏，0 表示未收藏)
return redis.call('BF.EXISTS', key, noteId)
