package com.lifepark.auth.sms;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "aliyun")
@Component
@Data
//用于接收配置文件 application-dev.yml中填写的阿里云 AccessKey 信息
public class AliyunAccessKeyProperties {
    private String accessKeyId;
    private String accessKeySecret;
}
