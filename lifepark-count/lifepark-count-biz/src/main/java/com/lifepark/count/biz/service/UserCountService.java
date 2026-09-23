package com.lifepark.count.biz.service;

import com.lifepark.framework.common.response.Response;
import com.lifepark.count.dto.FindUserCountsByIdReqDTO;
import com.lifepark.count.dto.FindUserCountsByIdRspDTO;

public interface UserCountService {
    Response<FindUserCountsByIdRspDTO> findUserCountData
            (FindUserCountsByIdReqDTO findUserCountsByIdReqDTO);
}
