package com.lifepark.kv.biz.service;

import com.lifepark.framework.common.response.Response;
import com.lifepark.kv.dto.req.AddNoteContentReqDTO;
import com.lifepark.kv.dto.req.DeleteNoteContentReqDTO;
import com.lifepark.kv.dto.req.FindNoteContentReqDTO;
import com.lifepark.kv.dto.rsp.FindNoteContentRspDTO;

public interface NoteContentService {
    Response<?> addNoteContent(AddNoteContentReqDTO addNoteContentReqDTO);

    Response<FindNoteContentRspDTO> findNoteConent(FindNoteContentReqDTO findNoteContentReqDTO);

    Response<?> deleteNoteContent(DeleteNoteContentReqDTO deleteNoteContentReqDTO);
}
