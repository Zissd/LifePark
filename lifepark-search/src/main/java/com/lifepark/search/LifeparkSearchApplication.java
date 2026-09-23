package com.lifepark.search;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.lifepark.search.domain.mapper")
public class LifeparkSearchApplication {
    public static void main(String[] args) {
        SpringApplication.run(LifeparkSearchApplication.class, args);
    }
}
