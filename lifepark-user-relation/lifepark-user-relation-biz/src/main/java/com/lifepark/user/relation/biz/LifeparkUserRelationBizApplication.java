package com.lifepark.user.relation.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

//启用Feign客户端的关键，它的主要作用是扫描所有带有@FeignClient注解的接口，并将它们注册为Spring容器中的Bean。
@EnableFeignClients(basePackages = "com.lifepark")
@SpringBootApplication
@MapperScan("com.lifepark.user.relation.biz.domain.mapper")
public class LifeparkUserRelationBizApplication {
    public static void main(String[] args) {
        SpringApplication.run(LifeparkUserRelationBizApplication.class, args);
    }
}

