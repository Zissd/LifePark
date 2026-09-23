package com.lifepark.count.biz.service;

import com.lifepark.framework.common.response.Response;
import com.lifepark.count.dto.FindNoteCountsByIdRspDTO;
import com.lifepark.count.dto.FindNoteCountsByIdsReqDTO;

import java.util.List;

public interface NoteCountService {
    Response<List<FindNoteCountsByIdRspDTO>> findNotesCountData
            (FindNoteCountsByIdsReqDTO findNoteCountsByIdsReqDTO);
}
