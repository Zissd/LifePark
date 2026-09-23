package com.lifepark.auth.controller;

import com.lifepark.framework.biz.operationlog.aspect.ApiOperationLog;
import com.lifepark.framework.common.response.Response;
import com.lifepark.auth.model.vo.user.UpdatePasswordReqVO;
import com.lifepark.auth.model.vo.user.UserLoginReqVO;
import com.lifepark.auth.service.AuthService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
public class AuthController {

    @Resource
    private AuthService authService;

    /**
     * 用户登录/注册
     * @param userLoginReqVO
     * @return
     */
    @PostMapping("/login")
    @ApiOperationLog(description = "用户登录/注册")
    public Response<String> loginAndRegister(
            @Validated @RequestBody UserLoginReqVO userLoginReqVO){
        return authService.loginAndRegister(userLoginReqVO);
    }

    /**
     * 用户退出登录
     */
    @PostMapping("/logout")
    @ApiOperationLog(description = "用户退出登录")
    public Response<?> logout(){

        return authService.logout();
    }

    /**
     * 修改密码
     * @param updatePasswordReqVO
     * @return
     */
    @PostMapping("/password/update")
    @ApiOperationLog(description = "修改密码")
    public Response<?> updatePassword(
            @Validated @RequestBody UpdatePasswordReqVO updatePasswordReqVO){
        return authService.updatePassword(updatePasswordReqVO);
    }

}
