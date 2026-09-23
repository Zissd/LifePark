package com.lifepark.user.api;

import com.lifepark.framework.common.response.Response;
import com.lifepark.user.constant.ApiConstants;
import com.lifepark.user.dto.req.*;
import com.lifepark.user.dto.resp.FindUserByIdRspDTO;
import com.lifepark.user.dto.resp.FindUserByPhoneRspDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

//@FeignClient 是用来标记这个接口是一个 Feign 客户端的注解。
//使用@FeignClient注解的接口会被 Spring 容器生成对应的 Bean

@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface UserFeignApi {

    String PREFIX = "/user";

    /**
     * 用户注册
     * @param registerUserReqDTO
     * @return
     */
    @PostMapping(value = PREFIX + "/register")
    Response<Long> registerUser(@RequestBody RegisterUserReqDTO registerUserReqDTO);

    /**
     * 根据手机号查询用户信息
     * @param findUserByPhoneReqDTO
     * @return
     */
   @PostMapping(value = PREFIX + "/findByPhone")
   Response<FindUserByPhoneRspDTO> findUserByPhone(
           @RequestBody FindUserByPhoneReqDTO findUserByPhoneReqDTO);

   /**
     * 修改密码
     * @param updateUserPasswordReqDTO
     * @return
     */
   @PostMapping(value = PREFIX + "/password/update")
    Response<Void> updatePassword(
            @RequestBody UpdateUserPasswordReqDTO updateUserPasswordReqDTO);

    /**
     * 根据用户 ID 查询用户信息
     * @param findUserByIdReqDTO
     * @return
     */
    @PostMapping(value = PREFIX + "/findById")
    Response<FindUserByIdRspDTO> findById(@RequestBody FindUserByIdReqDTO findUserByIdReqDTO);

    /**
     * 批量查询用户信息
     * @param findUsersByIdsReqDTO
     * @return
     */
    @PostMapping(value = PREFIX + "/findByIds")
    Response<List<FindUserByIdRspDTO>> findByIds(@RequestBody FindUsersByIdsReqDTO findUsersByIdsReqDTO);

}
