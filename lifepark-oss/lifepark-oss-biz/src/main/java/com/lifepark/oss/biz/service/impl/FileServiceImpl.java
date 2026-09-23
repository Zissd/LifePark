package com.lifepark.oss.biz.service.impl;

import com.lifepark.framework.common.response.Response;
import com.lifepark.oss.biz.service.FileService;
import com.lifepark.oss.biz.strategy.FileStrategy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


@Service
@Slf4j
public class FileServiceImpl implements FileService {
    //调用者直接面向 FileStrategy 接口，至于其底层具体的实现策略类，无需关心，直接调用相关方法就行了。
    @Resource
    private FileStrategy fileStrategy;

    private static final String BUCKET_NAME = "lifepark-zss";
    @Override
    public Response<?> uploadFile(MultipartFile file) {
        // 上传文件到
        String url = fileStrategy.uploadFile(file, BUCKET_NAME);

        return Response.success(url);
    }
}

