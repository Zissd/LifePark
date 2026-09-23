package com.lifepark.oss.biz.strategy.impl;

import cn.hutool.core.lang.UUID;
import com.aliyun.oss.OSS;
import com.lifepark.oss.biz.config.AliyunOSSProperties;
import com.lifepark.oss.biz.strategy.FileStrategy;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;


@Slf4j
public class AliyunOSSFileStrategy implements FileStrategy  {

    @Resource
    private AliyunOSSProperties aliyunOSSProperties;
    //阿里云OSS客户端，（多态）
    @Resource
    private OSS ossClient;
    @Override
    @SneakyThrows
    public String uploadFile(MultipartFile file, String bucketName) {
        log.info("## 上传文件至阿里云 OSS ...");
        //判断文件是否为空
        if(file.isEmpty()){
            throw new RuntimeException("文件不能为空");
        }
        //拿到文件原始名字与类型
        String originalFileName = file.getOriginalFilename();
        //生成存储对象的新名称（将 UUID 字符串中的 - 替换成空字符串）
        String key = UUID.randomUUID().toString().replace("-", "");
        //获取原文件文件后缀
        String suffix = originalFileName.substring(originalFileName.lastIndexOf("."));
        //拼接生成完整的文件名
        String objectName = String.format("%s%s", key, suffix);
        log.info("==> 开始上传文件至阿里云 OSS, ObjectName: {}", objectName);
        ossClient.putObject(bucketName, objectName,
                new ByteArrayInputStream(file.getInputStream().readAllBytes()));
        //拼接文件的访问链接并返回shu
        //https://lifepark-zss.oss-cn-beijing.aliyuncs.com/78875e590b2b4898ae257bba8e4d77cd.jpg
        String url = String.format("https://%s.%s/%s",bucketName, aliyunOSSProperties.getEndpoint(),  objectName);
        log.info("==> 上传文件至阿里云 OSS 成功，访问路径: {}", url);
        return url;
    }
}
