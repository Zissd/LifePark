package com.lifepark.note.biz.service;

import com.lifepark.framework.common.response.Response;
import com.lifepark.note.biz.model.vo.*;

public interface NoteService {
    Response<?> publishNote(PublishNoteReqVO publishNoteReqVO);

    Response<FindNoteDetailRspVO> findNoteDetail(FindNoteDetailReqVO findNoteDetailReqVO);

    Response<?> updateNote(UpdateNoteReqVO updateNoteReqVO);

    //删除本地笔记缓存
    void deleteNoteLocalCache(Long noteId);

    Response<?> deleteNote(DeleteNoteReqVO deleteNoteReqVO);

    Response<?> visibleOnlyMe(UpdateNoteVisibleOnlyMeReqVO updateNoteVisibleOnlyMeReqVO);

    Response<?> topNote(TopNoteReqVO topNoteReqVO);

    Response<?> likeNote(LikeNoteReqVO likeNoteReqVO);

    Response<?> unlikeNote(UnlikeNoteReqVO unlikeNoteReqVO);

    Response<?> collectNote(CollectNoteReqVO collectNoteReqVO);

    Response<?> unCollectNote(UnCollectNoteReqVO unCollectNoteReqVO);

    Response<FindPublishedNoteListRspVO> findPublishedNoteList(FindPublishedNoteListReqVO findPublishedNoteListReqVO);
}
