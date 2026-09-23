package com.lifepark.data.align;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.lifepark.data.align.domain.mapper")
public class LifeparkDataAlignApplication {
    public static void main(String[] args) {
        SpringApplication.run(LifeparkDataAlignApplication.class, args);
    }
}
