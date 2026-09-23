package com.lifepark.count.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.github.phantomthief.collection.BufferTrigger;
import com.google.common.collect.Lists;
import com.lifepark.framework.common.util.JsonUtils;
import com.lifepark.count.biz.constant.MQConstants;
import com.lifepark.count.biz.constant.RedisKeyConstants;
import com.lifepark.count.biz.domain.mapper.CommentDOMapper;
import com.lifepark.count.biz.enums.CommentLevelEnum;
import com.lifepark.count.biz.model.dto.CountPublishCommentMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

//计数: 笔记二级评论数

@Component
@RocketMQMessageListener(consumerGroup = "lifepark_group_child_comment_total"
        + MQConstants.TOPIC_COUNT_NOTE_COMMENT, // Group 组
        topic = MQConstants.TOPIC_COUNT_NOTE_COMMENT // 主题 Topic
)
@Slf4j
public class CountNoteChildCommentConsumer implements RocketMQListener<String> {

    @Resource
    private CommentDOMapper commentDOMapper;
    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    private BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大容量
            .batchSize(1000)   // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(1)) // 多久聚合一次（1s 一次）
            .setConsumerEx(this::consumeMessage) // 设置消费者方法
            .build();

    @Override
    public void onMessage(String body) {
        // 往 bufferTrigger 中添加元素
        bufferTrigger.enqueue(body);
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【笔记二级评论数】聚合消息, size: {}", bodys.size());
        log.info("==> 【笔记二级评论数】聚合消息, {}", JsonUtils.toJsonString(bodys));

        // 将聚合后的消息体 Json 转 List<CountPublishCommentMqDTO>
        List<CountPublishCommentMqDTO> countPublishCommentMqDTOList = Lists.newArrayList();
        bodys.forEach(body -> {
            try {
                List<CountPublishCommentMqDTO> list = JsonUtils.parseList(body, CountPublishCommentMqDTO.class);
                countPublishCommentMqDTOList.addAll(list);
            } catch (Exception e) {
                log.error("", e);
            }
        });

        // 过滤出二级评论，并按 parent_id 分组
        Map<Long, List<CountPublishCommentMqDTO>> groupMap = countPublishCommentMqDTOList
                .stream()
                .filter(commentMqDTO -> Objects.equals(CommentLevelEnum.TWO.getCode(), commentMqDTO.getLevel()))
                //按parent_id分组 即按一级评论id分组
                .collect(Collectors.groupingBy(CountPublishCommentMqDTO::getParentId));

        // 若无二级评论，则直接 return
        if (CollUtil.isEmpty(groupMap)) return;

        // 循环分组字典
        for (Map.Entry<Long, List<CountPublishCommentMqDTO>> entry : groupMap.entrySet()) {
            // 一级评论 ID
            Long parentId = entry.getKey();
            // 该一级评论id下的二级评论数
            int count = CollUtil.size(entry.getValue());

            // 更新hash中当前评论的子评论总数
            // 构建 Key
            String commentCountHashKey = RedisKeyConstants.buildCountCommentKey(parentId);
            // 判断 Hash 是否存在
            boolean hasKey = redisTemplate.hasKey(commentCountHashKey);

            // 若 Hash 存在，则更新子评论总数
            if (hasKey) {
                // 累加
                redisTemplate.opsForHash()
                        .increment(commentCountHashKey, RedisKeyConstants.FIELD_CHILD_COMMENT_TOTAL, count);
            }

            // 更新一级评论的下级评论总数，进行累加操作
            commentDOMapper.updateChildCommentTotal(parentId, count);
        }

//当一级评论被评论后，即下级评论总数增加入库后，需要更新一级评论的评论热度值

        // 获取字典中所有评论 ID
//调用Map接口的keySet()方法，返回该Map中所有键组成的Set。使用Set目标是为了去重，避免某一评论热度值在下游重复计算。
        Set<Long> commentIds = groupMap.keySet();
        // 异步发送计数 MQ, 更新评论热度值
        org.springframework.messaging.Message<String> message = MessageBuilder
                .withPayload(JsonUtils.toJsonString(commentIds))
                .build();

        // 异步发送 MQ 消息 把这一批一级评论id列表给评论服务 进行评论的热度值更新
        rocketMQTemplate.asyncSend(
                MQConstants.TOPIC_COMMENT_HEAT_UPDATE, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【评论热度值更新】MQ 发送成功，SendResult: {}", sendResult);
            }
            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【评论热度值更新】MQ 发送异常: ", throwable);
            }
        });
    }
}
