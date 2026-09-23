package com.lifepark.auth.service;

import com.lifepark.framework.common.response.Response;
import com.lifepark.auth.model.vo.verificationcode.SendVerificationCodeReqVO;


public interface VerificationCodeService {
    Response<?> send(SendVerificationCodeReqVO sendVerificationCodeReqVO);
}
