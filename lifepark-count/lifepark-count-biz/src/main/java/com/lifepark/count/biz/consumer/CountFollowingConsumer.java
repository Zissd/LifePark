package com.lifepark.count.biz.consumer;

import com.lifepark.framework.common.util.JsonUtils;
import com.lifepark.count.biz.constant.MQConstants;
import com.lifepark.count.biz.constant.RedisKeyConstants;
import com.lifepark.count.biz.enums.FollowUnfollowTypeEnum;
import com.lifepark.count.biz.model.dto.CountFollowUnfollowMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Objects;

//计数: 关注数
@Component
@RocketMQMessageListener(consumerGroup = "lifepark_group_"
        + MQConstants.TOPIC_COUNT_FOLLOWING, // Group 组
        topic = MQConstants.TOPIC_COUNT_FOLLOWING // 主题 Topic
)
@Slf4j
public class CountFollowingConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 消费用户数
     * @param body
     */
    @Override
    public void onMessage(String body) {
        log.info("## 消费到了 MQ 【计数: 关注数】, {}...", body);

        if (StringUtils.isBlank(body)) return;

        // 关注数和粉丝数计数场景不同，单个用户无法短时间内关注大量用户，所以无需聚合
        // 直接对 Redis 中的 Hash 进行 +1 或 -1 操作即可
        //将消息体 body 解析为 CountFollowUnfollowMqDTO 实体类；
        CountFollowUnfollowMqDTO countFollowUnfollowMqDTO =
                JsonUtils.parseObject(body, CountFollowUnfollowMqDTO.class);
        // 操作类型：关注 or 取关
        Integer type = countFollowUnfollowMqDTO.getType();
        // 原用户ID
        Long userId = countFollowUnfollowMqDTO.getUserId();
        // 构造Redis  Key
        String redisKey = RedisKeyConstants.buildCountUserKey(userId);
        // 判断 Hash 是否存在
        boolean isExisted = redisTemplate.hasKey(redisKey);
        // 若存在
        if (isExisted) {
            // 关注数：关注 +1， 取关 -1
            long count = Objects.equals(type, FollowUnfollowTypeEnum.FOLLOW.getCode()) ? 1 : -1;
            // 对 Hash 中的 followingTotal 字段进行加减操作
            redisTemplate.opsForHash().increment(redisKey, RedisKeyConstants.FIELD_FOLLOWING_TOTAL, count);
        }

        // 关注数据入库，再发送一个 MQ, 可以单独进行令牌桶削峰，进一步对流量进行控制，防止打垮数据库。
        // 构建消息对象
        Message<String> message = MessageBuilder.withPayload(body)
                .build();
        // 异步发送 MQ 消息 主题是关注数计数入库
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_FOLLOWING_2_DB, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数服务：关注数入库】MQ 发送成功，SendResult: {}", sendResult);
            }
            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数服务：关注数入库】MQ 发送异常: ", throwable);
            }
        });

    }
}

