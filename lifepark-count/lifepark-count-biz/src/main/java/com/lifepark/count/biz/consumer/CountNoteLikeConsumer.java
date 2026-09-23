package com.lifepark.count.biz.consumer;

import com.github.phantomthief.collection.BufferTrigger;
import com.google.common.collect.Lists;
import com.lifepark.framework.common.util.JsonUtils;
import com.lifepark.count.biz.constant.MQConstants;
import com.lifepark.count.biz.constant.RedisKeyConstants;
import com.lifepark.count.biz.enums.LikeUnlikeNoteTypeEnum;
import com.lifepark.count.biz.model.dto.AggregationCountLikeUnlikeNoteMqDTO;
import com.lifepark.count.biz.model.dto.CountLikeUnlikeNoteMqDTO;
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

//计数: 笔记点赞数

@Component
@RocketMQMessageListener(consumerGroup = "lifepark_group_"
        + MQConstants.TOPIC_LIKE_OR_UNLIKE, // Group 组
        topic = MQConstants.TOPIC_LIKE_OR_UNLIKE // 主题 Topic
)
@Slf4j
public class CountNoteLikeConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    //声明bufferTrigger来流量聚合
    private BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大容量
            .batchSize(1000)   // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(1)) // 多久聚合一次
            .setConsumerEx(this::consumeMessage) // 设置消费者方法
            .build();
    @Override
    public void onMessage(String body) {
        // 往 bufferTrigger 中添加元素
        bufferTrigger.enqueue(body);
    }

    private void consumeMessage(List<String> bodys) {

        log.info("==> 【笔记点赞数】聚合消息, size: {}", bodys.size());
        log.info("==> 【笔记点赞数】聚合消息, {}", JsonUtils.toJsonString(bodys));
        // List<String> 转 List<CountLikeUnlikeNoteMqDTO>
        List<CountLikeUnlikeNoteMqDTO> countLikeUnlikeNoteMqDTOS = bodys
                .stream()
                .map(body -> JsonUtils.parseObject(body, CountLikeUnlikeNoteMqDTO.class))
                .toList();
        // 按点赞的笔记 ID 进行分组
        Map<Long, List<CountLikeUnlikeNoteMqDTO>> groupMap = countLikeUnlikeNoteMqDTOS
                .stream()
                .collect(Collectors.groupingBy(CountLikeUnlikeNoteMqDTO::getNoteId));
//        // 按组汇总数据，统计出最终的计数
//        // 定义最终map，，，key 为笔记 ID, value 为最终操作的计数
//        Map<Long, Integer> countMap = Maps.newHashMap();
//
//        for (Map.Entry<Long, List<CountLikeUnlikeNoteMqDTO>> entry : groupMap.entrySet()) {
//            List<CountLikeUnlikeNoteMqDTO> list = entry.getValue();
//            // 最终的计数值，默认为 0
//            int finalCount = 0;
//            for (CountLikeUnlikeNoteMqDTO countLikeUnlikeNoteMqDTO : list) {
//                // 获取操作类型
//                Integer type = countLikeUnlikeNoteMqDTO.getType();
//                // 根据操作类型，获取对应枚举
//                LikeUnlikeNoteTypeEnum likeUnlikeNoteTypeEnum = LikeUnlikeNoteTypeEnum.valueOf(type);
//                // 若枚举为空，跳到下一次循环
//                if (Objects.isNull(likeUnlikeNoteTypeEnum)) continue;
//                switch (likeUnlikeNoteTypeEnum) {
//                    case LIKE -> finalCount += 1; // 如果为点赞操作，点赞数 +1
//                    case UNLIKE -> finalCount -= 1; // 如果为取消点赞操作，点赞数 -1
//                }
//            }
//            // 将分组后统计出的最终计数，存入 countMap 中
//            countMap.put(entry.getKey(), finalCount);
//        }
//        log.info("## 【笔记点赞数】聚合后的计数数据: {}", JsonUtils.toJsonString(countMap));
//
//        // 更新 Redis
//        countMap.forEach((k, v) -> {
//            // 笔记维度的点赞总数      count:note:user   存储hash结构
//            String redisKey = RedisKeyConstants.buildCountNoteKey(k);
//            // 判断 Redis 中 Hash 是否存在
//            boolean isExisted = redisTemplate.hasKey(redisKey);
//            // 若存在才会更新
//            // (因为缓存设有过期时间，考虑到过期后，缓存会被删除，这里需要判断一下，存在才会去更新，而初始化工作放在查询计数来做)
//            if (isExisted) {
//                // 对目标用户 Hash 中的点赞数字段进行计数操作
//                redisTemplate.opsForHash().increment(redisKey, RedisKeyConstants.FIELD_LIKE_TOTAL, v);
//            }
//        });
//
//        //更新 Redis之后 再异步发送MQ  修改数据库
//        //将上步中存储每个笔记 key 要修改的点赞数 countMap
//        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(countMap)).build();
        // 按组汇总数据，统计出最终的计数
        // 最终操作的计数对象
        List<AggregationCountLikeUnlikeNoteMqDTO> countList = Lists.newArrayList();
        for (Map.Entry<Long, List<CountLikeUnlikeNoteMqDTO>> entry : groupMap.entrySet()) {
            // 笔记 ID
            Long noteId = entry.getKey();
            // 笔记发布者 ID
            Long creatorId = null;
            List<CountLikeUnlikeNoteMqDTO> list = entry.getValue();
            // 最终的计数值，默认为 0
            int finalCount = 0;
            for (CountLikeUnlikeNoteMqDTO countLikeUnlikeNoteMqDTO : list) {
                // 设置笔记发布者用户 ID
                creatorId = countLikeUnlikeNoteMqDTO.getNoteCreatorId();
                // 获取操作类型
                Integer type = countLikeUnlikeNoteMqDTO.getType();
                // 根据操作类型，获取对应枚举
                LikeUnlikeNoteTypeEnum likeUnlikeNoteTypeEnum = LikeUnlikeNoteTypeEnum.valueOf(type);
                // 若枚举为空，跳到下一次循环
                if (Objects.isNull(likeUnlikeNoteTypeEnum)) continue;
                switch (likeUnlikeNoteTypeEnum) {
                    case LIKE -> finalCount += 1; // 如果为点赞操作，点赞数 +1
                    case UNLIKE -> finalCount -= 1; // 如果为取消点赞操作，点赞数 -1
                }
            }
            // 将分组后统计出的最终计数，存入 countList 中
            countList.add(AggregationCountLikeUnlikeNoteMqDTO
                    .builder()
                    .noteId(noteId)
                    .creatorId(creatorId)
                    .count(finalCount)
                    .build());
        }
        log.info("## 【笔记点赞数】聚合后的计数数据: {}", JsonUtils.toJsonString(countList));
        // 遍历 countList  更新 Redis 中的笔记维度的Hash与用户维度的Hash
        countList.forEach(item -> {
            // 笔记发布者 ID
            Long creatorId = item.getCreatorId();
            // 笔记 ID
            Long noteId = item.getNoteId();
            // 聚合后的计数
            Integer count = item.getCount();
            // 笔记维度计数 Redis Key
            String countNoteRedisKey = RedisKeyConstants.buildCountNoteKey(noteId);
            // 判断 Redis 中 Hash 是否存在
            boolean isCountNoteExisted = redisTemplate.hasKey(countNoteRedisKey);
            // 若存在才会更新，因为缓存设有过期时间，笔记维度计数Hash初始化工作放在查询计数来做
            if (isCountNoteExisted) {
                // 对目标笔记 Hash 中的点赞数字段进行计数操作
                redisTemplate.opsForHash()
                        .increment(countNoteRedisKey, RedisKeyConstants.FIELD_LIKE_TOTAL, count);
            }
            // 用户维度计数 Hash Key
            String countUserRedisKey = RedisKeyConstants.buildCountUserKey(creatorId);
            boolean isCountUserExisted = redisTemplate.hasKey(countUserRedisKey);
            // 若存在才会更新，因为缓存设有过期时间，用户维度计数Hash初始化工作放在查询计数来做
            if (isCountUserExisted) {
                // 对笔记发布者 Hash 中的收获点赞数字段进行计数操作
                redisTemplate.opsForHash()
                        .increment(countUserRedisKey, RedisKeyConstants.FIELD_LIKE_TOTAL, count);
            }
        });
        // 发送 MQ, 笔记点赞数据落库（笔记点赞统计库与用户获赞统计库）
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(countList))
                .build();
        //发送主题是统计笔记点赞数的消息
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_NOTE_LIKE_2_DB, message , new SendCallback(){
            public void onSuccess(SendResult sendResult){
                log.info("==> 【计数服务：笔记点赞数入库】MQ 发送成功，SendResult: {}", sendResult);
            }
            public void onException(Throwable throwable){
                log.error("==> 【计数服务：笔记点赞数入库】MQ 发送异常: ", throwable);
            }
        });

    }

}

