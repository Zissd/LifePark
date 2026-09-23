package com.lifepark.user.biz.service;

import com.lifepark.framework.common.response.Response;
import com.lifepark.user.biz.model.vo.FindUserProfileReqVO;
import com.lifepark.user.biz.model.vo.FindUserProfileRspVO;
import com.lifepark.user.biz.model.vo.UpdateUserInfoReqVO;
import com.lifepark.user.dto.req.*;
import com.lifepark.user.dto.resp.FindUserByIdRspDTO;
import com.lifepark.user.dto.resp.FindUserByPhoneRspDTO;

import java.util.List;

public interface UserService {

    Response<?> updateUserInfo(UpdateUserInfoReqVO updateUserInfoReqVO);

    Response<Long> register(RegisterUserReqDTO registerUserReqDTO);

    Response<FindUserByPhoneRspDTO> findByPhone(FindUserByPhoneReqDTO findUserByPhoneReqDTO);

    Response<?> updatePassword(UpdateUserPasswordReqDTO updateUserPasswordReqDTO);

    Response<FindUserByIdRspDTO> findById(FindUserByIdReqDTO findUserByIdReqDTO);

    Response<List<FindUserByIdRspDTO>> findByIds(FindUsersByIdsReqDTO findUsersByIdsReqDTO);

    Response<FindUserProfileRspVO> findUserProfile(FindUserProfileReqVO findUserProfileReqVO);
}
