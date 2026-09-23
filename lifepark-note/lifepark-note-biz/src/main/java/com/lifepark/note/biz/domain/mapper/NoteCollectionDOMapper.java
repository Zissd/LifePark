package com.lifepark.note.biz.domain.mapper;

import com.lifepark.note.biz.domain.dataobject.NoteCollectionDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NoteCollectionDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(NoteCollectionDO record);

    int insertSelective(NoteCollectionDO record);

    NoteCollectionDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(NoteCollectionDO record);

    int updateByPrimaryKey(NoteCollectionDO record);

    // 查询笔记是否被收藏
    int selectCountByUserIdAndNoteId(@Param("userId") Long userId, @Param("noteId") Long noteId);
    // 查询用户已收藏的笔记
    List<NoteCollectionDO> selectByUserId(Long userId);
    // 查询笔记是否已经被收藏
    int selectNoteIsCollected(@Param("userId") Long userId, @Param("noteId") Long noteId);
    // 查询用户最近收藏的笔记
    List<NoteCollectionDO> selectCollectedByUserIdAndLimit(@Param("userId") Long userId, @Param("limit")  int limit);
    // 新增笔记收藏记录，若已存在，则更新笔记收藏记录
    int insertOrUpdate(NoteCollectionDO noteCollectionDO);
    // 取消收藏
    int update2UnCollectByUserIdAndNoteId(NoteCollectionDO noteCollectionDO);


}