--当查询数据库不存在点赞记录时，需要执行此脚本，直接将当前点赞的笔记 ID, 添加到布隆过滤器中，并设置过期时间。


-- 操作的 Key
local key = KEYS[1]
local noteId = ARGV[1] -- 笔记ID
local expireSeconds = ARGV[2] -- 过期时间（秒）

redis.call("BF.ADD", key, noteId)
-- 设置过期时间
redis.call("EXPIRE", key, expireSeconds)
return 0
