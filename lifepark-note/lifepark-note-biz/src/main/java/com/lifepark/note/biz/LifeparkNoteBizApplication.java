package com.lifepark.note.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@MapperScan("com.lifepark.note.biz.domain.mapper")
@SpringBootApplication
@EnableFeignClients(basePackages = "com.lifepark")
//该注解告诉 Spring 容器：
// 开启 Feign 功能，自动扫描并注册被 @FeignClient 注解标记的接口，生成代理实现类
public class LifeparkNoteBizApplication {
    public static void main(String[] args) {
        SpringApplication.run(LifeparkNoteBizApplication.class,args);
    }
}
