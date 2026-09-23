package com.lifepark.auth.controller;

import com.lifepark.framework.biz.operationlog.aspect.ApiOperationLog;
import com.lifepark.framework.common.response.Response;
import com.lifepark.auth.model.vo.verificationcode.SendVerificationCodeReqVO;
import com.lifepark.auth.service.VerificationCodeService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VerificationCodeController {

    @Resource
    private VerificationCodeService verificationCodeService;

    /**
     * 发送验证码
     * @param sendVerificationCodeReqVO
     * @return
     */
    @PostMapping("/verification/code/send")
    @ApiOperationLog(description = "发送短信验证码")
    public Response<?> sendVerificationCode(
            @Validated @RequestBody SendVerificationCodeReqVO sendVerificationCodeReqVO) {
        return verificationCodeService.send(sendVerificationCodeReqVO);
    }
}
