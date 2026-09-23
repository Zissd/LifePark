package com.lifepark.user.relation.biz.consumer;

import com.google.common.util.concurrent.RateLimiter;
import com.lifepark.framework.common.util.DateUtils;
import com.lifepark.framework.common.util.JsonUtils;
import com.lifepark.user.relation.biz.constant.MQConstants;
import com.lifepark.user.relation.biz.constant.RedisKeyConstants;
import com.lifepark.user.relation.biz.domain.dataobject.FansDO;
import com.lifepark.user.relation.biz.domain.dataobject.FollowingDO;
import com.lifepark.user.relation.biz.domain.mapper.FansDOMapper;
import com.lifepark.user.relation.biz.domain.mapper.FollowingDOMapper;
import com.lifepark.user.relation.biz.enums.FollowUnfollowTypeEnum;
import com.lifepark.user.relation.biz.model.dto.CountFollowUnfollowMqDTO;
import com.lifepark.user.relation.biz.model.dto.FollowUserMqDTO;
import com.lifepark.user.relation.biz.model.dto.UnfollowUserMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Objects;

//MQ 消费者----实现关注、取关数据库入库以及Redis中粉丝列表缓存的更新，最后发送MQ通知计数服务

//consumerGroup后加上Topic ，保证每个 Topic 拥有独立的 group
//如果多个消费者订阅了不同的 topic，但由于它们在同一个 consumer group 中，
// 可能会影响消息的负载均衡，从而导致消息的消费效率变低。
//对于不同的 topic，可以使用不同的 consumer group。每个 group 独立消费自己订阅的 topic，
// 这样可以避免消息消费不均衡或竞争问题，从而导致消费速度变慢，亦或消费不到的问题发生。
@Component
@RocketMQMessageListener(consumerGroup = "lifepark_group" + MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW,
        topic = MQConstants.TOPIC_FOLLOW_OR_UNFOLLOW ,
        consumeMode = ConsumeMode.ORDERLY              //设置为顺序模式消费
//针对发送到单个队列的顺序消息，消费端不能够并发去消费，否则还是会乱序。
//  messageModel默认集群模式（点对点模式），只有一个服务实例去消费该队列。
)
// messageModel = MessageModel.BROADCASTING 广播模式
@Slf4j
public class FollowUnfollowConsumer implements RocketMQListener<Message> {

    @Resource
    private FollowingDOMapper followingDOMapper;
    @Resource
    private FansDOMapper fansDOMapper;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private RateLimiter rateLimiter;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(Message message) {
        //每秒生成的令牌数由配置文件上传到nacos配置中心来动态修改 每秒5000
        // 流量削峰：通过获取令牌来执行MQ，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();
        // 解析消息体
        String bodyJsonStr = new String(message.getBody());
        // 解析标签
        String tags = message.getTags();
        log.info("==> FollowUnfollowConsumer 消费了消息 {}, tags: {}", bodyJsonStr, tags);
        // 根据 MQ 标签，判断操作类型
        if (Objects.equals(tags, MQConstants.TAG_FOLLOW)) { // 关注
            handleFollowTagMessage(bodyJsonStr);
        } else if (Objects.equals(tags, MQConstants.TAG_UNFOLLOW)) { // 取关
            handleUnfollowTagMessage(bodyJsonStr);
        }
    }

    /**
     * 关注
     * @param bodyJsonStr
     */
    private void handleFollowTagMessage(String bodyJsonStr) {
        // 将消息体 Json 字符串转为 DTO 对象
        FollowUserMqDTO followUserMqDTO = JsonUtils.parseObject(bodyJsonStr, FollowUserMqDTO.class);
        // 判空
        if (Objects.isNull(followUserMqDTO)) return;

        // 此处的幂等性：在数据库中通过联合唯一索引保证
        // 计数服务的幂等性通过补偿机制来实现

        Long userId = followUserMqDTO.getUserId();
        Long followUserId = followUserMqDTO.getFollowUserId();
        LocalDateTime createTime = followUserMqDTO.getCreateTime();
        // 编程式提交事务
        boolean isSuccess = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            try {
                // 关注成功需往数据库添加两条记录
                // 关注表：一条记录
                int count = followingDOMapper.insert(FollowingDO.builder()
                        .userId(userId)
                        .followingUserId(followUserId)
                        .createTime(createTime)
                        .build());
                //如果关注表插入失败，粉丝表也没必要再插入了 ，减少操作数据库次数
                // 粉丝表：一条记录
                if (count > 0) {
                    fansDOMapper.insert(FansDO.builder()
                            .userId(followUserId)
                            .fansUserId(userId)
                            .createTime(createTime)
                            .build());
                }
                return true;
            } catch (Exception ex) {
                status.setRollbackOnly(); // 标记事务为回滚
                log.error("", ex);
            }
            return false;
        }));
        log.info("## 数据库添加记录结果：{}", isSuccess);
        // 若数据库操作成功，更新 Redis 中被关注用户的 ZSet 粉丝列表
        if (isSuccess) {
            // Lua 脚本
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptSource(new ResourceScriptSource(
                    new ClassPathResource("/lua/follow_check_and_update_fans_zset.lua")));
            script.setResultType(Long.class);
            // 时间戳
            long timestamp = DateUtils.localDateTime2Timestamp(createTime);
            // 构建被关注用户的粉丝列表 Redis Key
            String fansRedisKey = RedisKeyConstants.buildUserFansKey(followUserId);
            // 执行脚本 脚本会检查粉丝列表是否存在，不存在就不做任何操作。
            redisTemplate.execute(script, Collections.singletonList(fansRedisKey), userId, timestamp);
//当数据库事务提交成功后，发送 2 条 MQ, 通知到计数服务，以分别对粉丝数、关注数进行计数。
            // 发送 MQ 通知计数服务：统计关注数与粉丝数
            // 构建消息体 DTO
            CountFollowUnfollowMqDTO countFollowUnfollowMqDTO = CountFollowUnfollowMqDTO.builder()
                    .userId(userId)
                    .targetUserId(followUserId)
                    .type(FollowUnfollowTypeEnum.FOLLOW.getCode()) // 关注
                    .build();
            // 发送 MQ
            //当事务提交成功后，分别发送 2 条不同主题的 MQ,
            //一条是统计粉丝数 一条是统计关注数
            sendMQ(countFollowUnfollowMqDTO);
        }
    }

    /**
     * 取关
     * @param bodyJsonStr
     */
    private void handleUnfollowTagMessage(String bodyJsonStr){
        // 将消息体 Json 字符串转为 DTO 对象
        UnfollowUserMqDTO unfollowUserMqDTO = JsonUtils.parseObject(bodyJsonStr, UnfollowUserMqDTO.class);
        // 判空
        if (Objects.isNull(unfollowUserMqDTO)) return;
        // 幂等性：通过联合唯一索引保证
        Long userId = unfollowUserMqDTO.getUserId();
        Long unfollowUserId = unfollowUserMqDTO.getUnfollowUserId();
        LocalDateTime createTime = unfollowUserMqDTO.getCreateTime();
        // 编程式提交事务
        boolean isSuccess = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            try {
                // 取关成功需要删除数据库两条记录
                // 关注表：一条记录
                int count = followingDOMapper.deleteByUserIdAndFollowingUserId(userId, unfollowUserId);
                // 粉丝表：一条记录
                if (count > 0) {
                    fansDOMapper.deleteByUserIdAndFansUserId(unfollowUserId, userId);
                }
                return true;
            } catch (Exception ex) {
                status.setRollbackOnly(); // 标记事务为回滚
                log.error("", ex);
            }
            return false;
        }));
        // 若数据库删除成功，更新 Redis，将自己从被取注用户的 ZSet 粉丝列表删除
        if (isSuccess) {
            // 被取关用户的粉丝列表 Redis Key
            String fansRedisKey = RedisKeyConstants.buildUserFansKey(unfollowUserId);
            // 删除指定粉丝
            //若缓存中没有粉丝列表，fansRedisKey不存在，返回值为 0（表示实际删除的元素数量），不会抛出异常
            redisTemplate.opsForZSet().remove(fansRedisKey, userId);
//当数据库事务提交成功后，发送 2 条 MQ, 通知到计数服务，以分别对粉丝数、关注数进行计数。
            // 发送 MQ 通知计数服务：统计关注数与粉丝数
            // 构建消息体 DTO
            CountFollowUnfollowMqDTO countFollowUnfollowMqDTO = CountFollowUnfollowMqDTO.builder()
                    .userId(userId)
                    .targetUserId(unfollowUserId)
                    .type(FollowUnfollowTypeEnum.UNFOLLOW.getCode()) // 取关
                    .build();
            // 发送 MQ
            //当事务提交成功后，分别发送 2 条 MQ,统计关注数与粉丝数
            sendMQ(countFollowUnfollowMqDTO);
        }
    }

    /**
     * 发送 MQ 通知计数服务
     * @param countFollowUnfollowMqDTO
     */
    private void sendMQ(CountFollowUnfollowMqDTO countFollowUnfollowMqDTO) {
        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        org.springframework.messaging.Message<String> message = MessageBuilder
                .withPayload(JsonUtils.toJsonString(countFollowUnfollowMqDTO))
                .build();

        // 发送 MQ 通知计数服务： 统计关注数 （计数服务消费者操作包括redis计数与写入数据库）
        //主题是 关注数计数
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_FOLLOWING, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数服务：关注数】MQ 发送成功，SendResult: {}", sendResult);
            }
            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数服务：关注数】MQ 发送异常: ", throwable);
            }
        });
        // 发送 MQ 通知计数服务：统计粉丝数（计数服务消费者操作包括redis计数与写入数据库）
        //主题是 粉丝计数
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_FANS, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数服务：粉丝数】MQ 发送成功，SendResult: {}", sendResult);
            }
            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数服务：粉丝数】MQ 发送异常: ", throwable);
            }
        });
    }
}

