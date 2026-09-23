package com.lifepark.user.biz.rpc;

import com.lifepark.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

//分布式 ID 生成服务

@Component
public class DistributedIdGeneratorRpcService {

    @Resource
    private DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;

    // Leaf 号段模式：LifePark ID 业务标识
    private static final String BIZ_TAG_LIFEPARK_ID = "leaf-segment-lifepark-id";

    // Leaf 号段模式：用户 ID 业务标识
    private static final String BIZ_TAG_USER_ID = "leaf-segment-user-id";

    // 调用分布式 ID 生成服务生成LifePark ID
    public String getLifeparkId() {
        return distributedIdGeneratorFeignApi.getSegmentId(BIZ_TAG_LIFEPARK_ID);
    }

    //调用分布式 ID 生成服务用户 ID
    public String getUserId() {
        return distributedIdGeneratorFeignApi.getSegmentId(BIZ_TAG_USER_ID);
    }

}

