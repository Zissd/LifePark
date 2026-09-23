package com.lifepark.oss.api;

import com.lifepark.framework.common.response.Response;
import com.lifepark.oss.config.FeignFormConfig;
import com.lifepark.oss.constant.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

//@FeignClient 是用来标记这个接口是一个 Feign 客户端的注解。
//使用@FeignClient注解的接口会被 Spring 容器生成对应的 Bean

@FeignClient(name = ApiConstants.SERVICE_NAME, configuration = FeignFormConfig.class)
public interface FileFeignApi {

    String PREFIX = "/file";

//将oss-biz微服务中的 /file/test 接口的 Feign 客户端，封装到 api 模块中了,
//有其他服务想要调用oss-biz对象存储服务的 /file/test 接口，只需引入 lifepark-oss-api 模块即可。
//注入 FileFeignApi 接口，并调用 test 方法即可。

    @PostMapping(value = PREFIX + "/test")
    Response<?> test();

    /**
     * 文件上传
     * @param file
     * @return
     */
    @PostMapping(value = PREFIX + "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Response<?> uploadFile(@RequestPart(value = "file") MultipartFile file);

}
