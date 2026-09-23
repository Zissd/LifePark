package com.lifepark.framework.biz.context.config;

import com.lifepark.framework.biz.context.interceptor.FeignRequestInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

//Feign 请求拦截器自动配置
//将刚刚自定义的 FeignRequestInterceptor 请求拦截器，自动注入到 Spring 容器中。
@AutoConfiguration
public class FeignContextAutoConfiguration {

    @Bean
    public FeignRequestInterceptor feignRequestInterceptor() {
        return new FeignRequestInterceptor();
    }
}

