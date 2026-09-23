package com.lifepark.user.biz.controller;

import com.lifepark.framework.biz.operationlog.aspect.ApiOperationLog;
import com.lifepark.framework.common.response.Response;
import com.lifepark.user.biz.model.vo.FindUserProfileReqVO;
import com.lifepark.user.biz.model.vo.FindUserProfileRspVO;
import com.lifepark.user.biz.model.vo.UpdateUserInfoReqVO;
import com.lifepark.user.biz.service.UserService;
import com.lifepark.user.dto.req.*;
import com.lifepark.user.dto.resp.FindUserByIdRspDTO;
import com.lifepark.user.dto.resp.FindUserByPhoneRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Resource
    private UserService userService;


    /**
     * 用户信息修改
     * @param updateUserInfoReqVO
     * @return
     */
    //consumes用于指定控制器方法可以处理的请求的 Content-Type 类型。是 Spring MVC 中用于过滤请求的重要属性。
    // 这里请求类型限制为 multipart/form-data 的请求
    @PostMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<?> updateUserInfo(@Validated UpdateUserInfoReqVO updateUserInfoReqVO) {
        return userService.updateUserInfo(updateUserInfoReqVO);
    }

    /**
     * 获取用户主页信息
     * @return
     */
    @PostMapping("/profile")
    public Response<FindUserProfileRspVO> findUserProfile(@Validated @RequestBody FindUserProfileReqVO findUserProfileReqVO) {
        return userService.findUserProfile(findUserProfileReqVO);
    }
    // ===================================== 对其他服务提供的接口 =====================================

    /**
     * 用户注册
     * @param registerUserReqDTO
     * @return
     */
    @PostMapping("/register")
    @ApiOperationLog(description = "用户注册")
    public Response<Long> register(@Validated @RequestBody RegisterUserReqDTO registerUserReqDTO) {
        return userService.register(registerUserReqDTO);
    }

    /**
     * 通过手机号查询用户
     * @param findUserByPhoneReqDTO
     * @return
     */
    @PostMapping("/findByPhone")
    @ApiOperationLog(description = "通过手机号查询用户")
    public Response<FindUserByPhoneRspDTO> findByPhone(
            @Validated @RequestBody FindUserByPhoneReqDTO findUserByPhoneReqDTO){
        return userService.findByPhone(findUserByPhoneReqDTO);
    }

    /**
     * 修改密码
     * @param updateUserPasswordReqDTO
     * @return
     */
    @PostMapping("/password/update")
    @ApiOperationLog(description = "修改密码")
    public Response<?> updatePassword(
            @Validated @RequestBody UpdateUserPasswordReqDTO updateUserPasswordReqDTO){
        return userService.updatePassword(updateUserPasswordReqDTO);
    }

    /**
     * 通过id查找用户信息
     * @param findUserByIdReqDTO
     * @return
     */
    @PostMapping("/findById")
    @ApiOperationLog(description = "通过id查找用户信息")
    public Response<FindUserByIdRspDTO> findById(
            @Validated @RequestBody FindUserByIdReqDTO findUserByIdReqDTO){
        return userService.findById(findUserByIdReqDTO);
    }

    /**
     * 批量查询用户信息
     * @param findUsersByIdsReqDTO
     * @return
     */
    @PostMapping("/findByIds")
    @ApiOperationLog(description = "批量查询用户信息")
    public Response<List<FindUserByIdRspDTO>> findByIds(
            @Validated @RequestBody FindUsersByIdsReqDTO findUsersByIdsReqDTO) {
        return userService.findByIds(findUsersByIdsReqDTO);
    }

}

