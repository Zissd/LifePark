package com.lifepark.note.biz.domain.mapper;

import com.lifepark.note.biz.domain.dataobject.NoteDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NoteDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(NoteDO record);

    int insertSelective(NoteDO record);

    //根据id查询笔记，并且status为1（正常展示 ）
    NoteDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(NoteDO record);

    int updateByPrimaryKey(NoteDO record);
    //只更新可见性 即设置仅自己可见
    int updateVisibleOnlyMe(NoteDO noteDO);
    //设置顶置笔记与取消顶置笔记
    int updateIsTop(NoteDO noteDO);
    //根据笔记id查询笔记数量(即笔记是否存在)
    int selectCountByNoteId(Long noteId);
    // 查询笔记的发布者用户 ID
    Long selectCreatorIdByNoteId(Long noteId);
    // 根据笔记创建者id与游标滚动分页查询笔记列表
    List<NoteDO> selectPublishedNoteListByUserIdAndCursor(@Param("creatorId") Long creatorId,
                                                          @Param("cursor") Long cursor);
}