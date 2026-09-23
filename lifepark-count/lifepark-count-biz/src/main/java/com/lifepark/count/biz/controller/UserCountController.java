package com.lifepark.count.biz.controller;

import com.lifepark.framework.biz.operationlog.aspect.ApiOperationLog;
import com.lifepark.framework.common.response.Response;
import com.lifepark.count.biz.service.UserCountService;
import com.lifepark.count.dto.FindUserCountsByIdReqDTO;
import com.lifepark.count.dto.FindUserCountsByIdRspDTO;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/count")
public class UserCountController {

    @Resource
    private UserCountService userCountService;

    @PostMapping(value = "/user/data")
    @ApiOperationLog(description = "获取用户计数数据")
    public Response<FindUserCountsByIdRspDTO> findUserCountData
            (@Validated @RequestBody FindUserCountsByIdReqDTO findUserCountsByIdReqDTO) {
        return userCountService.findUserCountData(findUserCountsByIdReqDTO);
    }

}
