package com.lifepark.user.relation.biz.domain.mapper;

import com.lifepark.user.relation.biz.domain.dataobject.FollowingDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FollowingDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(FollowingDO record);

    int insertSelective(FollowingDO record);

    FollowingDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(FollowingDO record);

    int updateByPrimaryKey(FollowingDO record);
    //根据当前用户id查询关注列表
    List<FollowingDO> selectByUserId(Long userId);
    //根据当前用户id和被关注用户id删除关注关系
    int deleteByUserIdAndFollowingUserId(@Param("userId") Long userId,
                                         @Param("unfollowUserId") Long unfollowUserId);
    //查询当前用户的关注总数
    long selectCountByUserId(Long userId);
    //分页查询当前用户的关注者的id
    List<FollowingDO> selectPageListByUserId(@Param("userId") Long userId,
                                             @Param("offset") long offset,
                                             @Param("limit") long limit);
    //查询关注用户列表
    List<FollowingDO> selectAllByUserId(Long userId);

}