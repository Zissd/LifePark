package com.lifepark.search.service;

import com.lifepark.framework.common.response.PageResponse;
import com.lifepark.search.model.vo.SearchUserReqVO;
import com.lifepark.search.model.vo.SearchUserRspVO;

public interface UserService {
    PageResponse<SearchUserRspVO> searchUser(SearchUserReqVO searchUserReqVO);
}
