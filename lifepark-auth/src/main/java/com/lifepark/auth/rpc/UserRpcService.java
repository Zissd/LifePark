package com.lifepark.auth.rpc;

import com.lifepark.framework.common.response.Response;
import com.lifepark.user.api.UserFeignApi;
import com.lifepark.user.dto.req.FindUserByPhoneReqDTO;
import com.lifepark.user.dto.req.RegisterUserReqDTO;
import com.lifepark.user.dto.req.UpdateUserPasswordReqDTO;
import com.lifepark.user.dto.resp.FindUserByPhoneRspDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

//封装用户服务的各种调用方法，供认证服务ServiceImpl调用

@Component
public class UserRpcService {

    @Resource
    private UserFeignApi userFeignApi;

    /**
     * 用户注册
     * @param phone
     * @return
     */
    public Long registerUser(String phone) {
        //构建用户微服务的入参ReqDTO
        RegisterUserReqDTO registerUserReqDTO = new RegisterUserReqDTO();
        registerUserReqDTO.setPhone(phone);
        //使用Feign调用用户微服务
        Response<Long> response = userFeignApi.registerUser(registerUserReqDTO);
        if (!response.isSuccess()) {
            return null;
        }
        //得到用户ID
        return response.getData();
    }

    /**
     * 根据手机号查询用户信息
     * @param phone
     * @return
     */
    public FindUserByPhoneRspDTO findUserByPhone(String phone) {
        //构建用户微服务的入参ReqDTO
        FindUserByPhoneReqDTO findUserByPhoneReqDTO = new FindUserByPhoneReqDTO();
        findUserByPhoneReqDTO.setPhone(phone);
        //使用Feign调用用户微服务
        Response<FindUserByPhoneRspDTO> response = userFeignApi.findUserByPhone(findUserByPhoneReqDTO);
        if (!response.isSuccess()) {
            return null;
        }
        //得到出参RspDTO，包括用户id
        return response.getData();
    }

    /**
     * 修改用户密码
     * @param encodedPassword
     */
    public void updatePassword(String encodedPassword){
        //构建用户微服务的入参ReqDTO
        UpdateUserPasswordReqDTO updateUserPasswordReqDTO = new UpdateUserPasswordReqDTO();
        updateUserPasswordReqDTO.setEncodePassword(encodedPassword);
        //使用Feign调用用户微服务
        userFeignApi.updatePassword(updateUserPasswordReqDTO);
    }

}

