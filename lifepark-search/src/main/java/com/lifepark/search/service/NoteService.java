package com.lifepark.search.service;

import com.lifepark.framework.common.response.PageResponse;
import com.lifepark.search.model.vo.SearchNoteReqVO;
import com.lifepark.search.model.vo.SearchNoteRspVO;

public interface NoteService {
    PageResponse<SearchNoteRspVO> searchNote(SearchNoteReqVO searchNoteReqVO);
}
