package com.lifepark.kv.biz.controller;

import com.lifepark.framework.biz.operationlog.aspect.ApiOperationLog;
import com.lifepark.framework.common.response.Response;
import com.lifepark.kv.biz.service.CommentContentService;
import com.lifepark.kv.dto.req.BatchAddCommentContentReqDTO;
import com.lifepark.kv.dto.req.BatchFindCommentContentReqDTO;
import com.lifepark.kv.dto.req.DeleteCommentContentReqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

//评论内容

@RestController
@RequestMapping("/kv")
@Slf4j
public class CommentContentController {
    @Resource
    private CommentContentService commentContentService;

    /**
     * 批量添加评论内容
     * @param batchAddCommentContentReqDTO
     * @return
     */
    @PostMapping(value = "/comment/content/batchAdd")
    public Response<?> batchAddCommentContent(
            @Validated @RequestBody BatchAddCommentContentReqDTO batchAddCommentContentReqDTO) {
        return commentContentService.batchAddCommentContent(batchAddCommentContentReqDTO);
    }

    /**
     * 批量查询评论内容
     * @param batchFindCommentContentReqDTO
     * @return
     */
    @PostMapping(value = "/comment/content/batchFind")
    @ApiOperationLog(description = "批量查询评论内容")
    public Response<?> batchFindCommentContent(@Validated @RequestBody BatchFindCommentContentReqDTO batchFindCommentContentReqDTO) {
        return commentContentService.batchFindCommentContent(batchFindCommentContentReqDTO);
    }

    @PostMapping(value = "/comment/content/delete")
    @ApiOperationLog(description = "删除评论内容")
    public Response<?> deleteCommentContent(@Validated @RequestBody DeleteCommentContentReqDTO deleteCommentContentReqDTO) {
        return commentContentService.deleteCommentContent(deleteCommentContentReqDTO);
    }


}

