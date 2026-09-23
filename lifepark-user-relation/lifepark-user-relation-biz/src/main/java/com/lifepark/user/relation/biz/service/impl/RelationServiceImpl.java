package com.lifepark.user.relation.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.lifepark.framework.biz.context.holder.LoginUserContextHolder;
import com.lifepark.framework.common.exception.BizException;
import com.lifepark.framework.common.response.PageResponse;
import com.lifepark.framework.common.response.Response;
import com.lifepark.framework.common.util.DateUtils;
import com.lifepark.framework.common.util.JsonUtils;
import com.lifepark.user.dto.resp.FindUserByIdRspDTO;
import com.lifepark.user.relation.biz.constant.MQConstants;
import com.lifepark.user.relation.biz.constant.RedisKeyConstants;
import com.lifepark.user.relation.biz.domain.dataobject.FansDO;
import com.lifepark.user.relation.biz.domain.dataobject.FollowingDO;
import com.lifepark.user.relation.biz.domain.mapper.FansDOMapper;
import com.lifepark.user.relation.biz.domain.mapper.FollowingDOMapper;
import com.lifepark.user.relation.biz.enums.LuaResultEnum;
import com.lifepark.user.relation.biz.enums.ResponseCodeEnum;
import com.lifepark.user.relation.biz.model.dto.FollowUserMqDTO;
import com.lifepark.user.relation.biz.model.dto.UnfollowUserMqDTO;
import com.lifepark.user.relation.biz.model.vo.*;
import com.lifepark.user.relation.biz.rpc.UserRpcService;
import com.lifepark.user.relation.biz.service.RelationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
public class RelationServiceImpl implements RelationService {

    @Resource
    private UserRpcService userRpcService;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private FollowingDOMapper followingDOMapper;
    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Resource
    private FansDOMapper fansDOMapper;

    /**
     * 关注用户
     * @param followUserReqVO
     * @return
     */
    @Override
    public Response<?> follow(FollowUserReqVO followUserReqVO) {
        Long followUserId = followUserReqVO.getFollowUserId();
        Long userId = LoginUserContextHolder.getUserId();
        // 校验：无法关注自己
        if (Objects.equals(userId, followUserId)) {
            throw new BizException(ResponseCodeEnum.CANT_FOLLOW_YOUR_SELF);
        }
        //校验关注的用户是否存在
        FindUserByIdRspDTO findUserByIdRspDTO = userRpcService.findById(followUserId);
        if (Objects.isNull(findUserByIdRspDTO)) {
            throw new BizException(ResponseCodeEnum.FOLLOW_USER_NOT_EXISTED);
        }
        String followingRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource
                (new ClassPathResource("/lua/follow_check_and_add.lua")));
        script.setResultType(Long.class);
        // 当前时间
        LocalDateTime now = LocalDateTime.now();
        // 当前时间转时间戳
        long timestamp = DateUtils.localDateTime2Timestamp(now);
        Long result = redisTemplate.execute
                (script, Collections.singletonList(followingRedisKey), followUserId, timestamp);
        // 校验 Lua 脚本执行结果（封装了方法 可能是关注用户已满或已经关注过，直接返回结果）
        checkLuaScriptResult(result);
        // ZSET 不存在
        if (Objects.equals(result, LuaResultEnum.ZSET_NOT_EXIST.getCode())) {
                //缓存不存在 去数据库中查询当前用户关注列表
                List<FollowingDO> followingDOS = followingDOMapper.selectByUserId(userId);
                // 过期时间 保底1天+随机秒数
                long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
                if (CollUtil.isEmpty(followingDOS)) {
                    //如果记录为空，说明是当前用户首次关注，直接ZADD这一条关注关系数据, 设置过期时间
                    DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
                    script2.setScriptSource(new ResourceScriptSource(
                            new ClassPathResource("/lua/follow_add_and_expire.lua")));
                    script2.setResultType(Long.class);
//Redis 的 Lua 脚本中，操作的键（key）需要通过 KEYS数组传递（脚本中通过KEYS[1]、KEYS[2]等获取）
//execute()中第一个参数是脚本，第二个参数是要操作的key的列表,
//即使脚本只操作一个键，也需要按照规范将其放入KEYS数组中,剩余的参数都是ARGV[1],ARGV[2]等
                    redisTemplate.execute(script2, Collections.singletonList(followingRedisKey),
                            followUserId, timestamp, expireSeconds);
                } else {
                    // 若记录不为空，则将关注关系数据全量同步到 Redis 中，并设置过期时间；
                    // 构建 Lua 参数
                    Object[] luaArgs = buildLuaArgs(followingDOS, expireSeconds);
                    // 执行 Lua 脚本，批量同步关注关系数据到 Redis 中
                    DefaultRedisScript<Long> script3 = new DefaultRedisScript<>();
                    script3.setScriptSource(new ResourceScriptSource(
                            new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
                    script3.setResultType(Long.class);
                    redisTemplate.execute(
                            script3, Collections.singletonList(followingRedisKey), luaArgs);
                    // 再次调用上面的 Lua 脚本：follow_check_and_add.lua , 将最新的关注关系添加进去
                    result = redisTemplate.execute(script,
                            Collections.singletonList(followingRedisKey), followUserId, timestamp);
                    //检查添加新关注关系脚本后的返回值
                    checkLuaScriptResult(result);
                }
            }
        // 发送 MQ
        FollowUserMqDTO followUserMqDTO = FollowUserMqDTO.builder()
                .userId(userId)
                .followUserId(followUserId)
                .createTime(now)
                .build();
        // 构建消息对象,表示要发送的实际消息内容。将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder
                .withPayload(JsonUtils.toJsonString(followUserMqDTO))
                .build();
        // 指定消息要发送到的目标主题（Topic），可选包含标签（Tag）。
        String destination = MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW + ":" + MQConstants.TAG_FOLLOW;
        log.info("==> 开始发送关注操作 MQ, 消息体: {}", followUserMqDTO);
        // 异步发送 MQ 消息，提升接口响应速度,
//asyncSend是异步非阻塞方法。SendCallback为回调方法，提供了发送成功和异常两个方法；
//当消息发送成功或失败后，RocketMQ 客户端会回调该接口的方法，控制台日志输出发送成功或失败
//hashKey是顺序消息发送时选择队列的关键参数。RocketMQ 使用这个 hashKey 来确定应该将消息发送到哪个队列
//以确保同一个 hashKey 的消息会按照顺序进入同一个队列，从而实现顺序消费。
        String hashKey = String.valueOf(userId);
        rocketMQTemplate.asyncSendOrderly(destination, message, hashKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> MQ 发送成功，SendResult: {}", sendResult);
            }
            @Override
            public void onException(Throwable throwable) {
                log.error("==> MQ 发送异常: ", throwable);
            }
        });
        return Response.success();
    }

    /**
     * 校验操作关注 ZSET 缓存 Lua 脚本结果，根据状态码抛出对应的业务异常
     * @param result
     */
    private static void checkLuaScriptResult(Long result) {
        LuaResultEnum luaResultEnum = LuaResultEnum.valueOf(result);
        if (Objects.isNull(luaResultEnum)) throw new RuntimeException("Lua 返回结果错误");
        // 校验 Lua 脚本执行结果
        switch (luaResultEnum) {
            // 关注数已达到上限
            case FOLLOW_LIMIT -> throw new BizException(ResponseCodeEnum.FOLLOWING_COUNT_LIMIT);
            // 已经关注了该用户
            case ALREADY_FOLLOWED -> throw new BizException(ResponseCodeEnum.ALREADY_FOLLOWED);
        }
    }

    /**
     * 取关用户
     * @param unfollowUserReqVO
     * @return
     */
    @Override
    public Response<?> unfollow(UnfollowUserReqVO unfollowUserReqVO) {
        // 要取关了用户 ID
        Long unfollowUserId = unfollowUserReqVO.getUnfollowUserId();
        // 当前登录用户 ID
        Long userId = LoginUserContextHolder.getUserId();
        // 无法取关自己
        if (Objects.equals(userId, unfollowUserId)) {
            throw new BizException(ResponseCodeEnum.CANT_UNFOLLOW_YOUR_SELF);
        }
        // 校验取关的用户是否存在
        FindUserByIdRspDTO findUserByIdRspDTO = userRpcService.findById(unfollowUserId);
        if (Objects.isNull(findUserByIdRspDTO)) {
            throw new BizException(ResponseCodeEnum.FOLLOW_USER_NOT_EXISTED);
        }
        // 必须是关注了的用户，才能取关
        String followingRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);
        //从redis缓存中查询该关注列表是否存在，如果不存在可能是缓存过期了，需要再去数据库中查询
        //如果存在，再看列表中是否存在该用户，不存在则抛出异常，存在就直接删除，使用lua脚本保证一致性
        //定义lua脚本
        DefaultRedisScript<Long> script=new DefaultRedisScript<>();
        //设置脚本路径
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("/lua/unfollow_check_and_delete.lua")));
        //设置脚本返回值
        script.setResultType(Long.class);
        //执行脚本
        Long result = redisTemplate.execute(
                script, Collections.singletonList(followingRedisKey), unfollowUserId);
        // 校验 Lua 脚本执行结果
        // 取关的用户不在当前用户的关注列表中，抛出异常
        if (Objects.equals(result, LuaResultEnum.NOT_FOLLOWED.getCode())) {
            throw new BizException(ResponseCodeEnum.NOT_FOLLOWED);
        }
        // 当前用户的关注列表不存在（有过期的可能，所以要去数据库中查，若存在，同步关注列表关系到缓存中）
        if (Objects.equals(result, LuaResultEnum.ZSET_NOT_EXIST.getCode())) {
            // 从数据库查询当前用户的关注关系记录
            List<FollowingDO> followingDOS = followingDOMapper.selectByUserId(userId);
            // 随机过期时间
            // 保底1天+随机秒数
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
            // 若记录为空，则表示还未关注任何人，提示还未关注对方
            if (CollUtil.isEmpty(followingDOS)) {
                throw new BizException(ResponseCodeEnum.NOT_FOLLOWED);
            } else { // 若记录不为空，则将关注关系数据全量同步到 Redis 中，并设置过期时间；
                // 构建 Lua 参数
                Object[] luaArgs = buildLuaArgs(followingDOS, expireSeconds);
                // 执行 Lua 脚本，批量同步关注关系数据到 Redis 中
                DefaultRedisScript<Long> script3 = new DefaultRedisScript<>();
                script3.setScriptSource(new ResourceScriptSource(
                        new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
                script3.setResultType(Long.class);
                redisTemplate.execute(script3, Collections.singletonList(followingRedisKey), luaArgs);

                // 再次调用上面的 Lua 脚本：unfollow_check_and_delete.lua , 将取关的用户删除
                result = redisTemplate.execute(
                        script, Collections.singletonList(followingRedisKey), unfollowUserId);
                // 再次校验结果
                if (Objects.equals(result, LuaResultEnum.NOT_FOLLOWED.getCode())) {
                    throw new BizException(ResponseCodeEnum.NOT_FOLLOWED);
                }
            }
        }

        // 发送 MQ 。删除数据库。以及从redis删除对方粉丝列表中的自己（若redis存在对方粉丝的缓存，不存在则不删）
        // 构建消息体 DTO
        UnfollowUserMqDTO unfollowUserMqDTO = UnfollowUserMqDTO.builder()
                .userId(userId)
                .unfollowUserId(unfollowUserId)
                .createTime(LocalDateTime.now())
                .build();
        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(unfollowUserMqDTO))
                .build();
        // destination用于在发送消息时同时指定 Topic 和 Tag
        // 通过冒号连接是RocketMQ 约定的指定消息 Tag 的方式：
        //冒号前是Topic主题名称，冒号后是Tag标签名称
        // 从Message中获取 Tag，通常是为了灵活处理同一 Topic 下的多个 Tag 时不同的判断逻辑
        String destination = MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW + ":" + MQConstants.TAG_UNFOLLOW;
        log.info("==> 开始发送取关操作 MQ, 消息体: {}", unfollowUserMqDTO);
        // 异步发送 MQ 消息，提升接口响应速度
        //hashKey是顺序消息发送时选择队列的关键参数。
        // RocketMQ 使用这个 hashKey 来确定应该将消息发送到哪个队列，
        // 以确保同一个 hashKey 的消息会按照顺序进入同一个队列，从而实现顺序消费。
        String hashKey = String.valueOf(userId);
        rocketMQTemplate.asyncSendOrderly(destination, message, hashKey ,new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> MQ 发送成功，SendResult: {}", sendResult);
            }
            @Override
            public void onException(Throwable throwable) {
                log.error("==> MQ 发送异常: ", throwable);
            }
        });
        return Response.success();
    }

    /**
     * 查询用户关注列表
     * @param findFollowingListReqVO
     * @return
     */
    @Override
    public PageResponse<FindFollowingUserRspVO> findFollowingList(
            FindFollowingListReqVO findFollowingListReqVO) {
        //拿到要查询的用户id
        Long userId = findFollowingListReqVO.getUserId();
        // 页码
        Integer pageNo = findFollowingListReqVO.getPageNo();
        // 先从 Redis 中查询
        String followingListRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);
        // zcard 查询目标用户关注列表 ZSet 的总大小
        long total = redisTemplate.opsForZSet().zCard(followingListRedisKey);
        // 返参
        List<FindFollowingUserRspVO> findFollowingUserRspVOS = null;
        // 固定每页展示 10 条数据
        long limit = 10;
        if (total > 0) { // 缓存中有数据
            // 计算一共多少页
            long totalPage = PageResponse.getTotalPage(total, limit);
            // 请求的页码超出了总页数
            if (pageNo > totalPage)
                return PageResponse.success(null, pageNo, total);
            // 准备从 Redis 中查询 ZSet 分页数据
            // 每页 10 个元素，计算偏移量 。偏移量offset：表示需要跳过的元素总数
            long offset = (pageNo - 1) * limit;
// 使用 ZREVRANGEBYSCORE 命令按 score 降序（最新关注的在前）获取元素，同时使用 LIMIT 子句实现分页
//注意：这里使用了 Double.POSITIVE_INFINITY负无穷 和 Double.NEGATIVE_INFINITY正无穷 作为分数范围
//明确表达 不限制分数范围 的意图
            // 因为关注列表最多有 1000 个元素，这样可以确保获取到所有的元素
            Set<Object> followingUserIdsSet = redisTemplate.opsForZSet()
                    .reverseRangeByScore
                    (followingListRedisKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, offset, limit);
            if (CollUtil.isNotEmpty(followingUserIdsSet)) {
                // 提取所有用户 ID 到List集合中
                List<Long> userIds = followingUserIdsSet
                        .stream()
                        .map(object -> Long.valueOf(object.toString()))
                        .toList();
                // RPC: 调用用户服务，根据id列表查询用户详细信息。并将DTO转VO返回
                findFollowingUserRspVOS = rpcUserServiceAndDTO2VO(userIds, findFollowingUserRspVOS);
            }
        }else{//  缓存中没有数据 从数据库中查询
            // 先查询记录总量
            long count = followingDOMapper.selectCountByUserId(userId);
            // 计算一共多少页  totalPage = count + limit - 1 / limit
            long totalPage = PageResponse.getTotalPage(count, limit);
            // 请求的页码超出了总页数
            if (pageNo > totalPage)
                return PageResponse.success(null, pageNo, count);
            // 偏移量 offset = (pageNo - 1 ) * limit
            long offset = PageResponse.getOffset(pageNo, limit);
            // 分页查询
            List<FollowingDO> followingDOS = followingDOMapper
                    .selectPageListByUserId(userId, offset, limit);
            // 赋值真实的记录总数
            total = count;
            // 若记录不为空
            if (CollUtil.isNotEmpty(followingDOS)) {
                // 提取所有关注用户 ID 到集合中
                List<Long> userIds = followingDOS
                        .stream()
                        .map(FollowingDO::getFollowingUserId)
                        .toList();
                // RPC: 调用用户服务，并将 DTO 转换为 VO
                findFollowingUserRspVOS = rpcUserServiceAndDTO2VO(userIds, findFollowingUserRspVOS);
                // 异步将关注列表全量同步到 Redis
                threadPoolTaskExecutor.submit(() -> syncFollowingList2Redis(userId));
            }
        }

        return PageResponse.success(findFollowingUserRspVOS, pageNo, total);
    }

    /**
     * RPC: 调用用户服务，根据id列表获取用户详细信息，并将 DTO 转换为 VO
     * @param userIds
     * @param findFollowingUserRspVOS
     * @return
     */
    private List<FindFollowingUserRspVO> rpcUserServiceAndDTO2VO(
            List<Long> userIds, List<FindFollowingUserRspVO> findFollowingUserRspVOS) {
        // RPC: 批量查询用户信息
        List<FindUserByIdRspDTO> findUserByIdRspDTOS = userRpcService.findByIds(userIds);
        // 若不为空，DTO 转 VO
        if (CollUtil.isNotEmpty(findUserByIdRspDTOS)) {
            findFollowingUserRspVOS = findUserByIdRspDTOS
                    .stream()
                    .map(dto -> FindFollowingUserRspVO.builder()
                            .userId(dto.getId())
                            .avatar(dto.getAvatar())
                            .nickname(dto.getNickName())
                            .introduction(dto.getIntroduction())
                            .build())
                    .toList();
        }
        return findFollowingUserRspVOS;
    }

    /**
     * 关注列表同步至 Redis （最多1000条）
     * @param userId
     */
    private void syncFollowingList2Redis(Long userId) {
        // 查询全量关注用户列表（1000位用户）
        List<FollowingDO> followingDOS = followingDOMapper.selectAllByUserId(userId);
        if (CollUtil.isNotEmpty(followingDOS)) {
            // 用户关注列表 Redis Key
            String followingListRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);
            // 随机过期时间
            // 保底1天+随机秒数
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
            // 构建 Lua 参数
            Object[] luaArgs = buildLuaArgs(followingDOS, expireSeconds);
            // 执行 Lua 脚本，批量同步关注关系数据到 Redis 中
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptSource(new ResourceScriptSource(
                    new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
            script.setResultType(Long.class);
            redisTemplate.execute(script, Collections.singletonList(followingListRedisKey), luaArgs);
        }
    }

    /**
     * 构建批量添加关注列表 Lua 脚本参数
     * @param followingDOS
     * @param expireSeconds
     * @return
     */
    private static Object[] buildLuaArgs(List<FollowingDO> followingDOS, long expireSeconds)
    {
        // 每个关注关系有 2 个参数（score 和 value），再加一个过期时间，共size() * 2 + 1个参数
        int argsLength = followingDOS.size() * 2 + 1;
        Object[] luaArgs = new Object[argsLength];
        int i = 0;
        //每个following有score和value  i每次循环加2  最后一个参数是 ZSet 的过期时间是公用的
        for (FollowingDO following : followingDOS) {
            // 关注时间作为 score
            luaArgs[i] = DateUtils.localDateTime2Timestamp(following.getCreateTime());
            // 关注的用户 ID 作为 ZSet value
            luaArgs[i + 1] = following.getFollowingUserId();
            i += 2;
        }
        luaArgs[argsLength - 1] = expireSeconds; // 最后一个参数是 ZSet 的过期时间
        return luaArgs;
    }


    /**
     * 查询用户粉丝列表
     * @param findFansListReqVO
     * @return
     */
    @Override
    public PageResponse<FindFansUserRspVO> findFansList(FindFansListReqVO findFansListReqVO) {
        // 想要查询的用户 ID
        Long userId = findFansListReqVO.getUserId();
        // 页码
        Integer pageNo = findFansListReqVO.getPageNo();
        // 先从 Redis 中查询
        String fansListRedisKey = RedisKeyConstants.buildUserFansKey(userId);
        // 查询目标用户粉丝列表 ZSet 的总大小
        long total = redisTemplate.opsForZSet().zCard(fansListRedisKey);
        // 返参
        List<FindFansUserRspVO> findFansUserRspVOS = null;
        // 每页展示 10 条数据
        long limit = 10;
        if (total > 0) { // 缓存中有数据
            // 计算一共多少页
            long totalPage = PageResponse.getTotalPage(total, limit);
            // 请求的页码超出了总页数
            if (pageNo > totalPage)
                return PageResponse.success(null, pageNo, total);
            // 准备从 Redis 中查询 ZSet 分页数据
            // 每页 10 个元素，计算偏移量
            long offset = PageResponse.getOffset(pageNo, limit);
            // 使用 ZREVRANGEBYSCORE 命令按 score 降序获取元素，同时使用 LIMIT 子句实现分页
            // 下限是无穷小，上限是无穷大明确表达 “不限制分数范围” 的意图
            Set<Object> followingUserIdsSet = redisTemplate.opsForZSet()
                    .reverseRangeByScore(fansListRedisKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, offset, limit);
            if (CollUtil.isNotEmpty(followingUserIdsSet)) {
                // 提取所有用户 ID 到集合中
                List<Long> userIds = followingUserIdsSet.stream().map(object -> Long.valueOf(object.toString())).toList();
                // RPC: 批量查询用户信息
                findFansUserRspVOS = rpcUserServiceAndCountServiceAndDTO2VO(userIds, findFansUserRspVOS);
            }
        }else { // 若 Redis 缓存中无数据，则查询数据库
            // 先查询记录总量
            total = fansDOMapper.selectCountByUserId(userId);
            // 计算一共多少页
            long totalPage = PageResponse.getTotalPage(total, limit);
            // 请求的页码超出了总页数（只允许查询前 500 页）
            if (pageNo > 500 || pageNo > totalPage)
                return PageResponse.success(null, pageNo, total);
            // 偏移量
            long offset = PageResponse.getOffset(pageNo, limit);
            // 分页查询
            List<FansDO> fansDOS = fansDOMapper.selectPageListByUserId(userId, offset, limit);
            // 若记录不为空
            if (CollUtil.isNotEmpty(fansDOS)) {
                // 提取所有粉丝用户 ID 到集合中
                List<Long> userIds = fansDOS.stream()
                        .map(FansDO::getFansUserId)
                        .toList();
                // RPC: 调用用户服务、计数服务，并将 DTO 转换为 VO
                findFansUserRspVOS = rpcUserServiceAndCountServiceAndDTO2VO(userIds, findFansUserRspVOS);
                // 异步将粉丝列表同步到 Redis（最多5000条）
                threadPoolTaskExecutor.submit(() -> syncFansList2Redis(userId));
            }
        }
        return PageResponse.success(findFansUserRspVOS, pageNo, total);
    }

    /**
     * RPC: 调用用户服务、计数服务，并将 DTO 转换为 VO 粉丝列表
     * @param userIds
     * @param findFansUserRspVOS
     * @return
     */
    private List<FindFansUserRspVO> rpcUserServiceAndCountServiceAndDTO2VO(
            List<Long> userIds, List<FindFansUserRspVO> findFansUserRspVOS) {
        // RPC: 批量查询用户信息
        List<FindUserByIdRspDTO> findUserByIdRspDTOS = userRpcService.findByIds(userIds);


        // 若不为空，DTO 转 VO
        if (CollUtil.isNotEmpty(findUserByIdRspDTOS)) {
            findFansUserRspVOS = findUserByIdRspDTOS
                    .stream()
                    .map(dto -> FindFansUserRspVO.builder()
                            .userId(dto.getId())
                            .avatar(dto.getAvatar())
                            .nickname(dto.getNickName())
                            .noteTotal(0L)
                            .fansTotal(0L)
                            .build())
                    .toList();
        }
        return findFansUserRspVOS;
    }

    /**
     * 粉丝列表同步到 Redis（最多5000条）
     * @param userId
     */
    private void syncFansList2Redis(Long userId) {
        // 查询粉丝列表（最多5000位用户）
        List<FansDO> fansDOS = fansDOMapper.select5000FansByUserId(userId);
        if (CollUtil.isNotEmpty(fansDOS)) {
            // 用户粉丝列表 Redis Key
            String fansListRedisKey = RedisKeyConstants.buildUserFansKey(userId);
            // 随机过期时间
            // 保底1天+随机秒数
            long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
            // 构建 Lua 参数
            Object[] luaArgs = buildFansZSetLuaArgs(fansDOS, expireSeconds);

            // 执行 Lua 脚本，批量同步关注关系数据到 Redis 中
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
            script.setResultType(Long.class);
            redisTemplate.execute(script, Collections.singletonList(fansListRedisKey), luaArgs);
        }
    }

    /**
     * 构建粉丝列表 Lua 脚本参数
     * @param fansDOS
     * @param expireSeconds
     * @return
     */
    private static Object[] buildFansZSetLuaArgs(List<FansDO> fansDOS, long expireSeconds) {
        // 每个粉丝关系有 2 个参数（score 和 value），再加一个过期时间
        int argsLength = fansDOS.size() * 2 + 1;
        Object[] luaArgs = new Object[argsLength];

        int i = 0;
        for (FansDO fansDO : fansDOS) {
            // 粉丝的关注时间作为 score
            luaArgs[i] = DateUtils.localDateTime2Timestamp(fansDO.getCreateTime());
            // 粉丝的用户 ID 作为 ZSet value
            luaArgs[i + 1] = fansDO.getFansUserId();
            i += 2;
        }

        luaArgs[argsLength - 1] = expireSeconds; // 最后一个参数是 ZSet 的过期时间
        return luaArgs;
    }




}
