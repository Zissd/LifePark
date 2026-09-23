package com.lifepark.kv.biz.service;

import com.lifepark.framework.common.response.Response;
import com.lifepark.kv.dto.req.BatchAddCommentContentReqDTO;
import com.lifepark.kv.dto.req.BatchFindCommentContentReqDTO;
import com.lifepark.kv.dto.req.DeleteCommentContentReqDTO;

public interface CommentContentService {
    Response<?> batchAddCommentContent(BatchAddCommentContentReqDTO batchAddCommentContentReqDTO);

    Response<?> batchFindCommentContent(BatchFindCommentContentReqDTO batchFindCommentContentReqDTO);

    Response<?> deleteCommentContent(DeleteCommentContentReqDTO deleteCommentContentReqDTO);
}
