package com.lifepark.comment.biz.service;

import com.lifepark.framework.common.response.PageResponse;
import com.lifepark.framework.common.response.Response;
import com.lifepark.comment.biz.model.vo.*;

public interface CommentService {
    Response<?> publishComment(PublishCommentReqVO publishCommentReqVO);

    PageResponse<FindCommentItemRspVO> findCommentPageList(FindCommentPageListReqVO findCommentPageListReqVO);

    PageResponse<FindChildCommentItemRspVO> findChildCommentPageList(FindChildCommentPageListReqVO findChildCommentPageListReqVO);

    Response<?> likeComment(LikeCommentReqVO likeCommentReqVO);

    Response<?> unlikeComment(UnLikeCommentReqVO unLikeCommentReqVO);

    Response<?> deleteComment(DeleteCommentReqVO deleteCommentReqVO);
    /**
     * 删除本地评论缓存
     * @param commentId
     */
    void deleteCommentLocalCache(Long commentId);
}
