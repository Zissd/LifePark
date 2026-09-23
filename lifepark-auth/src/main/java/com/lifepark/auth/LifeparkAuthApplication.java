package com.lifepark.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
// 扫描 Mapper--------------Mapper接口无需额外添加Mapper注解
//@MapperScan("com/lifepark/auth/domain/mapper")
@EnableFeignClients(basePackages = "com.lifepark")
public class LifeparkAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(LifeparkAuthApplication.class, args);
    }

}
