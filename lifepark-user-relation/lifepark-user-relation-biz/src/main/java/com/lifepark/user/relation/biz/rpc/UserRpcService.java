package com.lifepark.user.relation.biz.rpc;

import com.lifepark.framework.common.response.Response;
import com.lifepark.user.api.UserFeignApi;
import com.lifepark.user.dto.req.FindUserByIdReqDTO;
import com.lifepark.user.dto.req.FindUsersByIdsReqDTO;
import com.lifepark.user.dto.resp.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

//用户服务
@Component
public class UserRpcService {

    @Resource
    private UserFeignApi userFeignApi;

    /**
     * 根据用户 ID 查询
     * @param userId
     * @return
     */
    public FindUserByIdRspDTO findById(Long userId) {
        FindUserByIdReqDTO findUserByIdReqDTO = new FindUserByIdReqDTO();
        findUserByIdReqDTO.setId(userId);

        Response<FindUserByIdRspDTO> response = userFeignApi.findById(findUserByIdReqDTO);

        if (!response.isSuccess() || Objects.isNull(response.getData())) {
            return null;
        }

        return response.getData();
    }

    /**
     * 根据用户 ID 列表查询用户信息
     * @param ids
     * @return
     */
    public List<FindUserByIdRspDTO> findByIds(List<Long> ids){
        FindUsersByIdsReqDTO findUsersByIdsReqDTO=new FindUsersByIdsReqDTO();
        findUsersByIdsReqDTO.setIds(ids);
        Response<List<FindUserByIdRspDTO>> response = userFeignApi.findByIds(findUsersByIdsReqDTO);
        if (!response.isSuccess() || Objects.isNull(response.getData())) {
            return null;
        }
        return response.getData();
    }


}

