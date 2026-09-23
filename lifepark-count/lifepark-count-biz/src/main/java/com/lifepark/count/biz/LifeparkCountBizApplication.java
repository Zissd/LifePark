package com.lifepark.count.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.lifepark.count.biz.domain.mapper")
@SpringBootApplication
public class LifeparkCountBizApplication {
    public static void main(String[] args) {
        SpringApplication.run(LifeparkCountBizApplication.class, args);
    }
}
