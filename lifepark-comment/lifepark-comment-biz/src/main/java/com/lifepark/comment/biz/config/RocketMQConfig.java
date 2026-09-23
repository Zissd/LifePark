package com.lifepark.comment.biz.config;

import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

// 读取RocketMQ 配置

@Configuration
@Import(RocketMQAutoConfiguration .class)
public class RocketMQConfig {
}
