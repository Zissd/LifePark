package com.lifepark.note.biz.rpc;

import com.lifepark.framework.common.response.Response;
import com.lifepark.kv.api.KeyValueFeignApi;
import com.lifepark.kv.dto.req.AddNoteContentReqDTO;
import com.lifepark.kv.dto.req.DeleteNoteContentReqDTO;
import com.lifepark.kv.dto.req.FindNoteContentReqDTO;
import com.lifepark.kv.dto.rsp.FindNoteContentRspDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

//RPC 调用 KV 键值微服务

@Component
public class KeyValueRpcService {

    @Resource
    private KeyValueFeignApi keyValueFeignApi;

    //保存KV笔记内容
    public boolean saveNoteContent(String uuid, String content) {
        //封装入参参数ReqDTO
        AddNoteContentReqDTO addNoteContentReqDTO = new AddNoteContentReqDTO();
        addNoteContentReqDTO.setUuid(uuid);
        addNoteContentReqDTO.setContent(content);

        //调用服务
        Response<?> response = keyValueFeignApi.addNoteContent(addNoteContentReqDTO);
        if (Objects.isNull(response) || !response.isSuccess()) {
            return false;
        }
        return true;
    }

    // 删除KV笔记内容
    public boolean deleteNoteContent(String uuid) {
        //封装入参参数ReqDTO
        DeleteNoteContentReqDTO deleteNoteContentReqDTO = new DeleteNoteContentReqDTO();
        deleteNoteContentReqDTO.setUuid(uuid);
        Response<?> response = keyValueFeignApi.deleteNoteContent(deleteNoteContentReqDTO);
        if (Objects.isNull(response) || !response.isSuccess()) {
            return false;
        }
        return true;
    }

    // 查询KV笔记内容
    public String findNoteContent(String uuid) {
        FindNoteContentReqDTO findNoteContentReqDTO = new FindNoteContentReqDTO();
        findNoteContentReqDTO.setUuid(uuid);

        Response<FindNoteContentRspDTO> response = keyValueFeignApi.findNoteContent(findNoteContentReqDTO);

        if (Objects.isNull(response) || !response.isSuccess() || Objects.isNull(response.getData())) {
            return null;
        }

        return response.getData().getContent();
    }

}
