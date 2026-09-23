package com.lifepark.user.relation.biz.domain.mapper;

import com.lifepark.user.relation.biz.domain.dataobject.FansDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FansDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(FansDO record);

    int insertSelective(FansDO record);

    FansDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(FansDO record);

    int updateByPrimaryKey(FansDO record);
    //根据当前用户ID和被关注用户ID删除粉丝关系
    int deleteByUserIdAndFansUserId(@Param("userId") Long userId,
                                    @Param("fansUserId") Long fansUserId);
    //查询当前用户ID的粉丝数量
    long selectCountByUserId(Long userId);
    //分页查询粉丝列表(仅查询粉丝id，后再根据ids查询详细信息)
    List<FansDO> selectPageListByUserId(@Param("userId") Long userId,
                                        @Param("offset") long offset,
                                        @Param("limit") long limit);
    //查询当前用户ID的最新的最多5000名粉丝
    List<FansDO> select5000FansByUserId(Long userId);
}