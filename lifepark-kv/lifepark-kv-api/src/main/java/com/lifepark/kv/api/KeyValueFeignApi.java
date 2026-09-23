package com.lifepark.kv.api;

// K-V 键值存储 Feign 接口

import com.lifepark.framework.common.response.Response;
import com.lifepark.kv.constant.ApiConstants;
import com.lifepark.kv.dto.req.*;
import com.lifepark.kv.dto.rsp.FindCommentContentRspDTO;
import com.lifepark.kv.dto.rsp.FindNoteContentRspDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

//@FeignClient 是用来标记这个接口是一个 Feign 客户端的注解。
//使用@FeignClient注解的接口会被 Spring 容器生成对应的 Bean

@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface KeyValueFeignApi {

    // Feign kv文本存储接口前缀
    String PREFIX = "/kv";

    // 新增笔记内容
    @PostMapping(value = PREFIX + "/note/content/add")
    Response<?> addNoteContent(@RequestBody AddNoteContentReqDTO addNoteContentReqDTO);

    // 查询笔记内容
    @PostMapping(value = PREFIX + "/note/content/find")
    Response<FindNoteContentRspDTO> findNoteContent(
            @RequestBody FindNoteContentReqDTO findNoteContentReqDTO);

    // 删除笔记内容
    @PostMapping(value = PREFIX + "/note/content/delete")
    Response<?> deleteNoteContent(@RequestBody DeleteNoteContentReqDTO deleteNoteContentReqDTO);

    // 批量新增评论内容
    @PostMapping(value = PREFIX + "/comment/content/batchAdd")
    Response<?> batchAddCommentContent(@RequestBody BatchAddCommentContentReqDTO batchAddCommentContentReqDTO);

    // 批量查询评论内容
    @PostMapping(value = PREFIX + "/comment/content/batchFind")
    Response<List<FindCommentContentRspDTO>> batchFindCommentContent(@RequestBody BatchFindCommentContentReqDTO batchFindCommentContentReqDTO);

    //删除评论内容
    @PostMapping(value = PREFIX + "/comment/content/delete")
    Response<?> deleteCommentContent(@RequestBody DeleteCommentContentReqDTO deleteCommentContentReqDTO);


}
