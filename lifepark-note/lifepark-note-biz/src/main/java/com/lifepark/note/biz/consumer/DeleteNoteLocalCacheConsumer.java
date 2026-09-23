package com.lifepark.note.biz.consumer;

import com.lifepark.note.biz.constant.MQConstants;
import com.lifepark.note.biz.service.NoteService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

//MQ 通知各笔记实例，删除本地缓存消息发送出去了，需要定义消费者，来消费消息
//监听并处理来自 RocketMQ 消息队列的消息，删除实例本地笔记缓存

@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "lifepark_group"
        + MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE,
        topic = MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, // 消费的主题 Topic
        messageModel = MessageModel.BROADCASTING)
// consumerGroup :消费组 , 标识一组消费者，用消费者组来区分不同的消息订阅者。
//  topic       : 消费的主题
// messageModel: BROADCASTING广播模式，同一消费组内部署的的所有消费者节点实例都会收到该主题的所有消息,
// 默认是集群模式，同消费组部署的多个实例中，只会有一个实例会通过负载均衡收到消息
public class DeleteNoteLocalCacheConsumer implements RocketMQListener<String>  {
                                    //  <String>指定该消费者将接收类型为 String 的消息。
    @Resource
    private NoteService noteService;

    //onMessage 这是 RocketMQListener 接口中必须实现的方法，当接收到消息时，此方法会被调用。
    @Override
    public void onMessage(String body) {
        Long noteId = Long.valueOf(body);
        //调用笔记服务，删除笔记实例的缓存
        noteService.deleteNoteLocalCache(noteId);
        log.info("## 消费者消费成功, noteId: {}", noteId);
    }

}
