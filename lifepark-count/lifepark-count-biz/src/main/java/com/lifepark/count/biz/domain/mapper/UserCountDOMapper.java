package com.lifepark.count.biz.domain.mapper;

import com.lifepark.count.biz.domain.dataobject.UserCountDO;
import org.apache.ibatis.annotations.Param;

public interface UserCountDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(UserCountDO record);

    int insertSelective(UserCountDO record);

    UserCountDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(UserCountDO record);

    int updateByPrimaryKey(UserCountDO record);
    // 添加或更新粉丝总数
    int insertOrUpdateFansTotalByUserId(@Param("count") Integer count, @Param("userId") Long userId);
    // 添加或更新关注总数
    int insertOrUpdateFollowingTotalByUserId(@Param("count") Integer count, @Param("userId") Long userId);
    // 添加记录或更新当前用户收到的笔记点赞总数
    int insertOrUpdateLikeTotalByUserId(@Param("count") Integer count, @Param("userId") Long userId);
    // 添加记录或更新笔记收藏总数
    int insertOrUpdateCollectTotalByUserId(@Param("count") Integer count, @Param("userId") Long userId);
    //添加记录或更新笔记发布数
    int insertOrUpdateNoteTotalByUserId(@Param("count") Long count, @Param("userId") Long userId);
    //根据用户 ID 查询用户的计数数据
    UserCountDO selectByUserId(Long userId);
}