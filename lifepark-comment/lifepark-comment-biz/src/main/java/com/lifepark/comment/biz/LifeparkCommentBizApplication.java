package com.lifepark.comment.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
@EnableFeignClients(basePackages = "com.lifepark")
@MapperScan("com.lifepark.comment.biz.domain.mapper")
public class LifeparkCommentBizApplication {
    public static void main(String[] args) {
        SpringApplication.run(LifeparkCommentBizApplication.class, args);
    }
}

