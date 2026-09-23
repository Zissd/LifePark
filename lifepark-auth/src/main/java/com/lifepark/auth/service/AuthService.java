package com.lifepark.auth.service;

import com.lifepark.framework.common.response.Response;
import com.lifepark.auth.model.vo.user.UpdatePasswordReqVO;
import com.lifepark.auth.model.vo.user.UserLoginReqVO;

public interface AuthService {
    Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO);

    Response<?> logout();

    Response<?> updatePassword(UpdatePasswordReqVO updatePasswordReqVO);
}
