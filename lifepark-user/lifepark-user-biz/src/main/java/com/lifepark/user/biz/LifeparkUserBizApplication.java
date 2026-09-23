package com.lifepark.user.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients(basePackages = "com.lifepark")
@MapperScan("com/lifepark/user/biz/domain/mapper")
@SpringBootApplication
public class LifeparkUserBizApplication {
    public static void main(String[] args) {
        SpringApplication.run(LifeparkUserBizApplication.class, args);
    }
}
