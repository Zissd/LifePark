package com.lifepark.oss.biz.factory;

import com.lifepark.oss.biz.strategy.FileStrategy;
import com.lifepark.oss.biz.strategy.impl.AliyunOSSFileStrategy;
import com.lifepark.oss.biz.strategy.impl.MinioFileStrategy;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//按需初始化具体的策略实现类
@Configuration
//@Configuration 通过 @Bean 注解的方法声明 Bean
@RefreshScope
//@RefreshScope nacos 配置中心动态刷新bean
public class FileStrategyFactory {
//读取配置文件中的 storage.type ，根据不同类型，初始化不同的策略实现类，并注入到 Spring 容器中。
// 这种方式可以保证， Spring 容器中只有自己需要的策略实现类，而不是都注入到 Spring 容器中去。
    @Value("${storage.type}")
    private String strategyType;

    @Bean
    @RefreshScope
    public FileStrategy getFileStrategy() {
        if (StringUtils.equals(strategyType, "minio")) {
            return new MinioFileStrategy();
        } else if (StringUtils.equals(strategyType, "aliyun")) {
            return new AliyunOSSFileStrategy();
        }

        throw new IllegalArgumentException("不可用的存储类型");
    }

}

