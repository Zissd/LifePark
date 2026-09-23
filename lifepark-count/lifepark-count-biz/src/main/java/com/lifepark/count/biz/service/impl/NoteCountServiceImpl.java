package com.lifepark.count.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.lifepark.framework.common.response.Response;
import com.lifepark.count.biz.constant.RedisKeyConstants;
import com.lifepark.count.biz.domain.dataobject.NoteCountDO;
import com.lifepark.count.biz.domain.mapper.NoteCountDOMapper;
import com.lifepark.count.biz.service.NoteCountService;
import com.lifepark.count.dto.FindNoteCountsByIdRspDTO;
import com.lifepark.count.dto.FindNoteCountsByIdsReqDTO;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class NoteCountServiceImpl implements NoteCountService {

    @Resource
    private RedisTemplate<String,Object> redisTemplate;
    @Resource
    private NoteCountDOMapper noteCountDOMapper;

    @Override
    public Response<List<FindNoteCountsByIdRspDTO>> findNotesCountData
            (FindNoteCountsByIdsReqDTO findNoteCountsByIdsReqDTO)
    {
        //从入参中拿到要查询计数的笔记id列表
        List<Long> noteIds = findNoteCountsByIdsReqDTO.getNoteIds();
        //从redis笔记 Hash 中获取笔记计数数据
        List<String> hashKeys = noteIds.stream()
                .map(RedisKeyConstants::buildCountNoteKey)
                .toList();
        // 使用 Pipeline 通道，从 Redis 中批量查询笔记 Hash 计数
        List<Object> countHashes = getCountHashesByPipelineFromRedis(hashKeys);

        // 返参 DTO 列表
        List<FindNoteCountsByIdRspDTO> findNoteCountsByIdRspDTOS = Lists.newArrayList();

        // 用于存放 Hash 缓存中不存在，需要查数据库的笔记 ID
        List<Long> noteIdsNeedQuery = Lists.newArrayList();

        // 循环入参的笔记 ID 集合，构建对应 DTO, 并设置缓存中已存在的计数，以及过滤出需要查数据库的笔记 ID
        for (int i = 0; i < noteIds.size(); i++) {
            Long currNoteId = noteIds.get(i);
            List<Integer> currCountHash = (List<Integer>) countHashes.get(i);

            // 点赞数、收藏数、评论数
            Integer likeTotal = currCountHash.get(0);
            Integer collectTotal = currCountHash.get(1);
            Integer commentTotal = currCountHash.get(2);

            // Hash 中存在任意一个 Field 为 null, 都需要查询数据库
            //Hash的字段为空的情况包含了Hash为空的情况
            if (Objects.isNull(likeTotal) || Objects.isNull(collectTotal) || Objects.isNull(commentTotal)) {
                noteIdsNeedQuery.add(currNoteId);
            }

            // 构建 Hash DTO 加入到出参列表中。不存在的默认设置为0，后续从数据库值覆盖
            FindNoteCountsByIdRspDTO findNoteCountsByIdRspDTO = FindNoteCountsByIdRspDTO.builder()
                    .noteId(currNoteId)
                    .likeTotal(Objects.nonNull(likeTotal) ? Long.valueOf(likeTotal) : null)
                    .collectTotal(Objects.nonNull(collectTotal) ? Long.valueOf(collectTotal) : null)
                    .commentTotal(Objects.nonNull(commentTotal) ? Long.valueOf(commentTotal) : null)
                    .build();

            findNoteCountsByIdRspDTOS.add(findNoteCountsByIdRspDTO);
        }

        // 所有 Hash 计数都存在于 Redis 中，直接返参
        if (CollUtil.isEmpty(noteIdsNeedQuery)) {
            return Response.success(findNoteCountsByIdRspDTOS);
        }

        // 2. 查询数据库
        // 从redis中批量查询过滤出的 noteIdsNeedQuery 笔记 ID
        List<NoteCountDO> noteCountDOS = noteCountDOMapper.selectByNoteIds(noteIdsNeedQuery);

        // 若数据库查询的记录不为空
        if (CollUtil.isNotEmpty(noteCountDOS)) {
            // DO 集合转 Map, 方便后续查询对应笔记 ID 的计数
            Map<Long, NoteCountDO> noteIdAndDOMap = noteCountDOS.stream()
                    .collect(Collectors.toMap(NoteCountDO::getNoteId, noteCountDO -> noteCountDO));
//noteIdAndDOMap是由数据库查询结果通过.stream()构建的，而noteCountDOS只包含数据库中实际存在的记录
            //将数据库笔记统计表中的计数数据同步到 Redis 笔记Hash中
            syncNoteHash2Redis(findNoteCountsByIdRspDTOS, noteIdAndDOMap);

            // 针对 DTO 中为 null 的计数字段，循环设置从数据库中查询到的计数
            for (FindNoteCountsByIdRspDTO findNoteCountsByIdRspDTO : findNoteCountsByIdRspDTOS) {
                Long noteId = findNoteCountsByIdRspDTO.getNoteId();
                Long likeTotal = findNoteCountsByIdRspDTO.getLikeTotal();
                Long collectTotal = findNoteCountsByIdRspDTO.getCollectTotal();
                Long commentTotal = findNoteCountsByIdRspDTO.getCommentTotal();

//                if (Objects.isNull(likeTotal))
//                    findNoteCountsByIdRspDTO.setLikeTotal(noteIdAndDOMap.get(noteId).getLikeTotal());
//                if (Objects.isNull(collectTotal))
//                    findNoteCountsByIdRspDTO.setCollectTotal(noteIdAndDOMap.get(noteId).getCollectTotal());
//                if (Objects.isNull(commentTotal))
//                    findNoteCountsByIdRspDTO.setCommentTotal(noteIdAndDOMap.get(noteId).getCommentTotal());
                // 先获取对应的DO对象，判断是否存在
                NoteCountDO noteCountDO = noteIdAndDOMap.get(noteId);
                if (noteCountDO != null) { // 只有DO存在时才赋值
                    if (Objects.isNull(likeTotal))
                        findNoteCountsByIdRspDTO.setLikeTotal(noteCountDO.getLikeTotal());
                    if (Objects.isNull(collectTotal))
                        findNoteCountsByIdRspDTO.setCollectTotal(noteCountDO.getCollectTotal());
                    if (Objects.isNull(commentTotal))
                        findNoteCountsByIdRspDTO.setCommentTotal(noteCountDO.getCommentTotal());
                } else {
                    // 可选：如果数据库中没有该记录，说明还没有点赞等记录，给默认值（比如0）
                    if (Objects.isNull(likeTotal)) findNoteCountsByIdRspDTO.setLikeTotal(0L);
                    if (Objects.isNull(collectTotal)) findNoteCountsByIdRspDTO.setCollectTotal(0L);
                    if (Objects.isNull(commentTotal)) findNoteCountsByIdRspDTO.setCommentTotal(0L);
                }
            }
        }

        return Response.success(findNoteCountsByIdRspDTOS);
    }

    /**
     * 从 Redis 中批量查询笔记 Hash 计数
     * @param hashKeys
     * @return
     */
    private List<Object> getCountHashesByPipelineFromRedis(List<String> hashKeys)
    {
        return redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) {
//循环 Redis Hash Key 集合，通过 multiGet() 方法，批量获取对应笔记的点赞数、收藏数、被评论数；
                for (String hashKey : hashKeys) {
                    // 批量获取多个字段
                    operations.opsForHash().multiGet(hashKey, List.of(
                            RedisKeyConstants.FIELD_LIKE_TOTAL,
                            RedisKeyConstants.FIELD_COLLECT_TOTAL,
                            RedisKeyConstants.FIELD_COMMENT_TOTAL
                    ));
                }
                return null;
            }
        });
    }

    /**
     * 将笔记 Hash 计数同步到 Redis 中
     * @param findNoteCountsByIdRspDTOS
     * @param noteIdAndDOMap
     */
    private void syncNoteHash2Redis(List<FindNoteCountsByIdRspDTO> findNoteCountsByIdRspDTOS, Map<Long, NoteCountDO> noteIdAndDOMap)
    {
        // 将笔记计数同步到 Redis 中
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) {
                // 循环已构建好的返参 DTO 集合
                for (FindNoteCountsByIdRspDTO findNoteCountsByIdRspDTO : findNoteCountsByIdRspDTOS) {
                    Long likeTotal = findNoteCountsByIdRspDTO.getLikeTotal();
                    Long collectTotal = findNoteCountsByIdRspDTO.getCollectTotal();
                    Long commentTotal = findNoteCountsByIdRspDTO.getCommentTotal();

                    // 若当前 DTO 的所有计数都不为空，则无需同步 Hash
                    if (Objects.nonNull(likeTotal) && Objects.nonNull(collectTotal) && Objects.nonNull(commentTotal)) {
                        continue;
                    }

                    // 否则，若有任意一个 Field 计数为空，则需要同步对应的 Field
                    Long noteId = findNoteCountsByIdRspDTO.getNoteId();
                    // 构建 Hash Key
                    String noteCountHashKey = RedisKeyConstants.buildCountNoteKey(noteId);

                    // 设置 Field 计数
                    Map<String, Long> countMap = Maps.newHashMap();
                    NoteCountDO noteCountDO = noteIdAndDOMap.get(noteId);

                    // 若该笔记的 DO 为空，即没有笔记统计数据，没有点赞量，收藏量，被评论数，则无需同步
                    if (Objects.isNull(noteCountDO)) {
                        continue;
                    }

                    if (Objects.isNull(likeTotal)) {
                        countMap.put(RedisKeyConstants.FIELD_LIKE_TOTAL, noteCountDO.getLikeTotal());
                    }
                    if (Objects.isNull(collectTotal)) {
                        countMap.put(RedisKeyConstants.FIELD_COLLECT_TOTAL, noteCountDO.getCollectTotal());
                    }
                    if (Objects.isNull(commentTotal)) {
                        countMap.put(RedisKeyConstants.FIELD_COMMENT_TOTAL, noteCountDO.getCommentTotal());
                    }

                    // 批量添加 Hash 的计数 Field
                    operations.opsForHash().putAll(noteCountHashKey, countMap);

                    // 设置随机过期时间 (1小时以内)
                    long expireTime = 60*30 + RandomUtil.randomInt(60 * 30);
                    operations.expire(noteCountHashKey, expireTime, TimeUnit.SECONDS);
                }

                return null;
            }
        });
    }

}
