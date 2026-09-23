--数据库记录不为空时：用于批量向ZSET中插入旧的关注关系数据，以及设置过期时间
-- 操作的 Key
local key = KEYS[1]
-- 准备批量添加数据的参数
local zaddArgs = {}
-- 遍历 ARGV 参数，按 [score1, value1, score2, value2, ...]将分数和值按顺序插入到 zaddArgs 中
for i = 1, #ARGV - 1, 2 do
    table.insert(zaddArgs, ARGV[i])      -- 分数（关注时间）
    table.insert(zaddArgs, ARGV[i+1])    -- 值（关注的用户ID）
end
-- 调用 ZADD 批量插入数据
--执行批量添加 。unpack 用于将数组元素展开为参数列表
--减少 Redis 命令执行次数。
--Redis 处理单个命令的开销远小于处理 N 个独立命令，尤其当元素数量较多，性能差距会非常明显。
redis.call('ZADD', key, unpack(zaddArgs))
-- 设置 ZSet 的过期时间
local expireTime = ARGV[#ARGV] -- 最后一个参数为过期时间
redis.call('EXPIRE', key, expireTime)
return 0


--通过 unpack(zaddArgs) 执行单次 ZADD 批量添加的写法更优，这种是循环添加的写法
--local key = KEYS[1]
--local expireSeconds = ARGV[#ARGV]  -- 最后一个参数是过期时间
--for i=1, #ARGV-1, 2 do
--    local score = ARGV[i]
--    local value = ARGV[i+1]
--    redis.call('ZADD', key, score, value)
--end
--redis.call('EXPIRE', key, expireSeconds)
--return 0