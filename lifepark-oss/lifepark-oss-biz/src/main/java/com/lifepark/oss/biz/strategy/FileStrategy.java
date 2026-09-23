package com.lifepark.oss.biz.strategy;

import org.springframework.web.multipart.MultipartFile;

//文件策略接口

public interface FileStrategy {

    /**
     * 文件上传
     *
     * @param file
     * @param bucketName
     * @return
     */
    String uploadFile(MultipartFile file, String bucketName);

}

