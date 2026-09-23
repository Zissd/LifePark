package com.lifepark.count.biz.consumer;

import com.github.phantomthief.collection.BufferTrigger;
import com.google.common.collect.Maps;
import com.lifepark.framework.common.util.JsonUtils;
import com.lifepark.count.biz.constant.MQConstants;
import com.lifepark.count.biz.constant.RedisKeyConstants;
import com.lifepark.count.biz.enums.FollowUnfollowTypeEnum;
import com.lifepark.count.biz.model.dto.CountFollowUnfollowMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

//计数: 粉丝数
@Component
@RocketMQMessageListener(consumerGroup = "lifepark_group_"
        + MQConstants.TOPIC_COUNT_FANS, // Group 组
        topic = MQConstants.TOPIC_COUNT_FANS // 主题 Topic
)
@Slf4j
public class CountFansConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String, Objects> redisTemplate;
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    //当 CountFansConsumer 被实例化时，会初始化 bufferTrigger 对象
    //初始化 BufferTrigger 对象
    private BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大消息容量
            .batchSize(1000)   // 一批次最多聚合 1000 条消息
            .linger(Duration.ofSeconds(1)) //规定若 1 秒内消息未凑满 1000 条，也会触发批处理。
            //设置满足条件时的批处理规则
            // this::consumeMessage ：表示调用当前类（CountFansConsumer）的 consumeMessage 方法。
            //即当条件满足，批处理触发时，调用 consumeMessage 方法来处理这批消息。
            .setConsumerEx(this::consumeMessage)
            .build();
    @Override   //body就是消息体message
    public void onMessage(String body) {
        //将消息添加到 bufferTrigger 的内部缓存队列中
        bufferTrigger.enqueue(body);
    }

    /**
     * 批量消费粉丝数消息
     * @param bodys
     */
    private void consumeMessage(List<String> bodys) {
        //流量聚合会传入批量消息列表 bodys
        log.info("==> 聚合消息, size: {}", bodys.size());
        log.info("==> 聚合消息, {}", JsonUtils.toJsonString(bodys));

        // List<String> 转 List<CountFollowUnfollowMqDTO>
        List<CountFollowUnfollowMqDTO> countFollowUnfollowMqDTOS = bodys
                .stream()
                .map(body -> JsonUtils.parseObject(body, CountFollowUnfollowMqDTO.class))
                .toList();
        // 按目标用户id进行分组groupingBy，因为这一批消息中，可能关注、取关的目标用户不一样；
        //id键为 Long 类型，值为CountFollowUnfollowMqDTO对象的列表
        Map<Long, List<CountFollowUnfollowMqDTO>> groupMap = countFollowUnfollowMqDTOS
                .stream()
                .collect(Collectors.groupingBy(CountFollowUnfollowMqDTO::getTargetUserId));
//Collectors.groupingBy(...) 是一个专门用于 “分组收集” 的收集器（Collector），它的作用是：
//根据传入的分类函数（这里是 CountFollowUnfollowMqDTO::getTargetUserId，即按 targetUserId 分组），
// 将流中的元素归类，最终返回一个 Map，
// 键（Key） 是分类函数的返回值（即 targetUserId 的值），
// 值（Value） 是该分组下所有元素组成的列表（List<CountFollowUnfollowMqDTO>）。

        // 按组汇总数据，统计出最终的计数
        // countMap 中 key 为目标用户ID, value 为最终操作的计数
        Map<Long, Integer> countMap = Maps.newHashMap();
        //Map.Entry专门用于表示 Map 中的 单个键值对 。groupMap.entrySet()获取groupMap中的所有元素
        //Map.Entry<Long, List<CountFollowUnfollowMqDTO>>
        //表示：一个包含目标用户 ID和该用户所有关注 / 取关操作记录列表的键值对对象
        for (Map.Entry<Long, List<CountFollowUnfollowMqDTO>> entry : groupMap.entrySet()) {
            //从entry的value中获取DTO list
            List<CountFollowUnfollowMqDTO> list = entry.getValue();
            // 最终的计数值，默认为 0
            int finalCount = 0;
            for (CountFollowUnfollowMqDTO countFollowUnfollowMqDTO : list) {
                // 获取操作类型(取关、关注)
                Integer type = countFollowUnfollowMqDTO.getType();
                // 根据操作类型，获取对应枚举
                FollowUnfollowTypeEnum followUnfollowTypeEnum = FollowUnfollowTypeEnum.valueOf(type);
                // 若枚举为空，跳到下一次循环
                if (Objects.isNull(followUnfollowTypeEnum))
                    continue;
                switch (followUnfollowTypeEnum) {
                    case FOLLOW -> finalCount += 1; // 如果为关注操作，粉丝数 +1
                    case UNFOLLOW -> finalCount -= 1; // 如果为取关操作，粉丝数 -1
                }
            }
            // 将分组后统计出的最终计数，存入 countMap 中
            //countMap 中 key 为目标用户ID, value 为最终操作的计数
            countMap.put(entry.getKey(), finalCount);
        }
        log.info("## 聚合后的计数数据: {}", JsonUtils.toJsonString(countMap));

        // 循环 countMap 更新 Redis
        countMap.forEach((k, v) -> {
            // 构建 Redis Key, 判断缓存是否存在
            String redisKey = RedisKeyConstants.buildCountUserKey(k);
            boolean isExisted = redisTemplate.hasKey(redisKey);
            // 若存在才会更新
            // 缓存设有过期时间，缓存会被删除，存在才会去更新，而初始化工作放在查询计数来做
            if (isExisted) {
                // 对目标用户 Hash 中的粉丝数字段fansTotal进行计数操作
                redisTemplate.opsForHash().increment(redisKey, RedisKeyConstants.FIELD_FANS_TOTAL, v);
            }
        });

        //计数数据落库，再发送一个 MQ,可以单独进行令牌桶削峰，进一步对流量进行控制，防止打垮数据库。
        // 构建消息体
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(countMap))
                .build();
        // 异步发送 MQ 消息，提升接口响应速度,主题：粉丝数计数入库
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_FANS_2_DB, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数服务：粉丝数入库】MQ 发送成功，SendResult: {}", sendResult);
            }
            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数服务：粉丝数入库】MQ 发送异常: ", throwable);
            }
        });
    }
}
