package com.lifepark.data.align.consumer;

import com.lifepark.framework.common.util.JsonUtils;
import com.lifepark.data.align.constant.MQConstants;
import com.lifepark.data.align.constant.RedisKeyConstants;
import com.lifepark.data.align.constant.TableConstants;
import com.lifepark.data.align.domain.mapper.InsertRecordMapper;
import com.lifepark.data.align.model.dto.NoteOperateMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Objects;

/**
 * @version: v1.0.0
 * @description: 日增量数据落库：笔记发布、删除
 **/
@Component
@RocketMQMessageListener(consumerGroup =
        "lifepark_group_data_align_" + MQConstants.TOPIC_NOTE_OPERATE, // Group 组
        topic = MQConstants.TOPIC_NOTE_OPERATE // 主题 Topic
)
@Slf4j
public class TodayNotePublishIncrementData2DBConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String,Object> redisTemplate;
    @Resource
    private InsertRecordMapper insertRecordMapper;

    /**
     * 表总分片数
     */
    @Value("${table.shards}")
    private int tableShards;

    @Override
    public void onMessage(String body) {
        log.info("## TodayNotePublishIncrementData2DBConsumer 消费到了 MQ: {}", body);
        //解析消息体
        NoteOperateMqDTO noteOperateMqDTO = JsonUtils.parseObject(body, NoteOperateMqDTO.class);
        if (noteOperateMqDTO == null)
            return;
        // 发布、被删除笔记发布者 ID
        Long noteCreatorId = noteOperateMqDTO.getCreatorId();
        //拿到当前日期
        String date = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd")); // 转字符串
        String Bloomkey = RedisKeyConstants.buildBloomUserNoteOperateListKey(date);
        //1.判断布隆过滤器是否存在  bloom:dataAlign:user:note:operators:date
        DefaultRedisScript<Long> script=new DefaultRedisScript<>();
        //设置路径
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/bloom_today_user_note_publish_check.lua")));
        //设置返回值
        script.setResultType(Long.class);
        Long result = redisTemplate.execute(script, Collections.singletonList(Bloomkey), noteCreatorId);
        log.info("布隆过滤器结果：{}", result);
        if(Objects.equals(result, 0L)){
            //布隆过滤器不存在或者 内容不存在
            // 根据分片总数，取模，分别获取对应的分片序号
            long userIdHashKey = noteCreatorId % tableShards;
            // 将日增量变更数据，写入日增量表中 这里只有一条语句，不用考虑原子性 事务式编程
            // - t_data_align_note_publish_count_temp_日期_分片序号
            insertRecordMapper.insert2DataAlignUserNotePublishCountTempTable
                    (TableConstants.buildTableNameSuffix(date, userIdHashKey), noteCreatorId);
            // 再加入到布隆过滤器当中
            //构造一句redis脚本语言
            RedisScript<Long> script1= RedisScript.of("return redis.call('BF.ADD',KEYS[1],ARGV[1])",Long.class);
            Long result2 = redisTemplate.execute(script1, Collections.singletonList(Bloomkey), noteCreatorId);
            log.info("布隆过滤器添加结果：{}", result2);
        }
    }


}
