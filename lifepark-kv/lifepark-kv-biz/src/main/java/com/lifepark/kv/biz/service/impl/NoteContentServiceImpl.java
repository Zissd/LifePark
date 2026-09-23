package com.lifepark.kv.biz.service.impl;

import com.lifepark.framework.common.exception.BizException;
import com.lifepark.framework.common.response.Response;
import com.lifepark.kv.biz.domain.dataobject.NoteContentDO;
import com.lifepark.kv.biz.domain.repository.NoteContentRepository;
import com.lifepark.kv.biz.enums.ResponseCodeEnum;
import com.lifepark.kv.biz.service.NoteContentService;
import com.lifepark.kv.dto.req.AddNoteContentReqDTO;
import com.lifepark.kv.dto.req.DeleteNoteContentReqDTO;
import com.lifepark.kv.dto.req.FindNoteContentReqDTO;
import com.lifepark.kv.dto.rsp.FindNoteContentRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class NoteContentServiceImpl implements NoteContentService {

    @Resource
    private NoteContentRepository noteContentRepository;

    /**
     * 新增笔记内容
     * @param addNoteContentReqDTO
     * @return
     */
    @Override
    public Response<?> addNoteContent(AddNoteContentReqDTO addNoteContentReqDTO) {
        //拿到笔记id    这里是string类型  后面要转化为UUID类型才能存入cassandra数据库
        String uuid = addNoteContentReqDTO.getUuid();
        //拿到笔记内容
        String content = addNoteContentReqDTO.getContent();
        //构建Cassandra数据库对象DO
        NoteContentDO noteContentDO=NoteContentDO.builder()
                .id(UUID.fromString(uuid))
                .content(content)
                .build();
        //存入Cassandra数据库
        noteContentRepository.save(noteContentDO);
        return Response.success();
    }

    /**
     * 查询笔记内容
     * @param findNoteContentReqDTO
     * @return
     */
    @Override
    public Response<FindNoteContentRspDTO> findNoteConent(
            FindNoteContentReqDTO findNoteContentReqDTO) {
        //拿到笔记id
        String uuid = findNoteContentReqDTO.getUuid();
        //根据笔记id查找内容
        Optional<NoteContentDO> optional = noteContentRepository.findById(UUID.fromString(uuid));
        // 若笔记内容不存在
        if (!optional.isPresent()) {
            throw new BizException(ResponseCodeEnum.NOTE_CONTENT_NOT_FOUND);
        }

        NoteContentDO noteContentDO = optional.get();
        //构建rspDTO对象，并返回
        FindNoteContentRspDTO findNoteContentRspDTO=FindNoteContentRspDTO
                .builder()
                .uuid(UUID.fromString(uuid))
                .content(noteContentDO.getContent())
                .build();

        return Response.success(findNoteContentRspDTO);
    }

    /**
     * 删除笔记内容
     * @param deleteNoteContentReqDTO
     * @return
     */
    @Override
    public Response<?> deleteNoteContent(DeleteNoteContentReqDTO deleteNoteContentReqDTO) {
        //拿到笔记id
        String uuid = deleteNoteContentReqDTO.getUuid();
        //删除笔记
        noteContentRepository.deleteById(UUID.fromString(uuid));
        return Response.success();
    }
}
