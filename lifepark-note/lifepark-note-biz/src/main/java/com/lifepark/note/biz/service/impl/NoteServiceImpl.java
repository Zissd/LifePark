package com.lifepark.note.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.lifepark.framework.biz.context.holder.LoginUserContextHolder;
import com.lifepark.framework.common.exception.BizException;
import com.lifepark.framework.common.response.Response;
import com.lifepark.framework.common.util.DateUtils;
import com.lifepark.framework.common.util.JsonUtils;
import com.lifepark.framework.common.util.NumberUtils;
import com.lifepark.count.dto.FindNoteCountsByIdRspDTO;
import com.lifepark.note.biz.constant.MQConstants;
import com.lifepark.note.biz.constant.RedisKeyConstants;
import com.lifepark.note.biz.domain.dataobject.NoteCollectionDO;
import com.lifepark.note.biz.domain.dataobject.NoteDO;
import com.lifepark.note.biz.domain.dataobject.NoteLikeDO;
import com.lifepark.note.biz.domain.mapper.NoteCollectionDOMapper;
import com.lifepark.note.biz.domain.mapper.NoteDOMapper;
import com.lifepark.note.biz.domain.mapper.NoteLikeDOMapper;
import com.lifepark.note.biz.domain.mapper.TopicDOMapper;
import com.lifepark.note.biz.enums.*;
import com.lifepark.note.biz.model.dto.CollectUnCollectNoteMqDTO;
import com.lifepark.note.biz.model.dto.LikeUnlikeNoteMqDTO;
import com.lifepark.note.biz.model.dto.NoteOperateMqDTO;
import com.lifepark.note.biz.model.vo.*;
import com.lifepark.note.biz.rpc.CountRpcService;
import com.lifepark.note.biz.rpc.DistributedIdGeneratorRpcService;
import com.lifepark.note.biz.rpc.KeyValueRpcService;
import com.lifepark.note.biz.rpc.UserRpcService;
import com.lifepark.note.biz.service.NoteService;
import com.lifepark.user.dto.resp.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NoteServiceImpl implements NoteService {

    @Resource
    private NoteDOMapper noteDOMapper;
    @Resource
    private TopicDOMapper topicDOMapper;
    @Resource
    private KeyValueRpcService keyValueRpcService;
    @Resource
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Resource
    private UserRpcService userRpcService;
    @Resource
    private RedisTemplate<String, String> redisTemplate;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Resource
    private NoteLikeDOMapper noteLikeDOMapper;
    @Resource
    private NoteCollectionDOMapper noteCollectionDOMapper;
    @Resource
    private CountRpcService countRpcService;

    // 笔记详情 Caffeine本地缓存
    private static final Cache<Long, String> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(10000) // 设置初始容量为 10000 个条目
            .maximumSize(10000) // 设置缓存的最大容量为 10000 个条目
            .expireAfterWrite(1, TimeUnit.HOURS) // 设置缓存条目在写入后 1 小时过期
            .build();


    /**
     * 发布笔记
     * @param publishNoteReqVO
     * @return
     */
    @Override
    public Response<?> publishNote(PublishNoteReqVO publishNoteReqVO) {
        //拿到笔记类型（图文 视频）
        Integer type = publishNoteReqVO.getType();
        //根据笔记类型拿到枚举类型
        NoteTypeEnum noteTypeEnum = NoteTypeEnum.valueOf(type);
        //是否为空 若为空 抛出异常 由全局异常处理器处理
        if (Objects.isNull(noteTypeEnum)) {
            throw new BizException(ResponseCodeEnum.NOTE_TYPE_ERROR);
        }
        //提前定义变量
        String imgUris = null;
        // 笔记内容是否为空，默认值为 true，即空
        Boolean isContentEmpty = true;
        String videoUri = null;
        //不同枚举类型(图文或视频)，进入不同的判断逻辑
        switch (noteTypeEnum) {
            case IMAGE_TEXT:
                List<String> imgUriList = publishNoteReqVO.getImgUris();
                //使用谷歌下的Preconditions.checkArgument来校验，省去我们编写if-else
                // 校验图片是否为空
                Preconditions.checkArgument(
                        CollUtil.isNotEmpty(imgUriList), "笔记图片不能为空");
                // 校验图片数量
                Preconditions.checkArgument(
                        imgUriList.size() <= 8, "笔记图片不能多于 8 张");
                // 将图片链接拼接，以逗号分隔
                imgUris = StringUtils.join(imgUriList, ",");
                break;
            case VIDEO:
                videoUri = publishNoteReqVO.getVideoUri();
                // 校验视频链接是否为空
                Preconditions.checkArgument(
                        StringUtils.isNotBlank(videoUri), "笔记视频不能为空");
                break;
        }

        // RPC: 调用分布式 ID 生成服务，生成sql数据库笔记 ID
        String snowflakeIdId = distributedIdGeneratorRpcService.getSnowflakeId();
        // 笔记内容 UUID（文本要在cassandra中存储的UUID）
        String contentUuid = null;
        // 笔记内容
        String content = publishNoteReqVO.getContent();
        // 若用户填写了笔记内容
        if (StringUtils.isNotBlank(content)) {
            // 内容是否为空，置为 false，即不为空
            isContentEmpty = false;
            // 生成笔记内容 UUID
            contentUuid = UUID.randomUUID().toString();
            // RPC: 调用 KV 键值服务，存储短文本
            boolean isSavedSuccess = keyValueRpcService.saveNoteContent(contentUuid, content);
            // 若存储失败，抛出业务异常，提示用户发布笔记失败
            if (!isSavedSuccess) {
                throw new BizException(ResponseCodeEnum.NOTE_PUBLISH_FAIL);
            }
        }
        // 话题（入参中 用户选择话题会传过来话题ID 这里要拿到话题名称，构建笔记DO）
        Long topicId = publishNoteReqVO.getTopicId();
        String topicName = null;
        if (Objects.nonNull(topicId)) {
            // 获取话题名称
            topicName = topicDOMapper.selectNameByPrimaryKey(topicId);
        }
        // 发布者用户 ID
        Long creatorId = LoginUserContextHolder.getUserId();
        // 构建笔记 DO 对象
        NoteDO noteDO = NoteDO.builder()
                .id(Long.valueOf(snowflakeIdId))
                .isContentEmpty(isContentEmpty)
                .creatorId(creatorId)
                .imgUris(imgUris)
                .title(publishNoteReqVO.getTitle())
                .topicId(publishNoteReqVO.getTopicId())
                .topicName(topicName)
                .type(type)
                .visible(NoteVisibleEnum.PUBLIC.getCode())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .status(NoteStatusEnum.NORMAL.getCode())
                .isTop(Boolean.FALSE)
                .videoUri(videoUri)
                .contentUuid(contentUuid)
                .build();
        //由于笔记元数据与内容分散在不同的数据库中，传统的事务无法使用。
        // 这里的小技巧是，将笔记内容先存储到cassandra，确认成功后，再在sql中插入笔记各种元数据
        // 如果在sql中插入笔记各种元数据失败了，再调用 KV 键值服务，将笔记内容删除，以保证数据一致性。

        // 第一次删除个人主页 - 已发布笔记列表缓存
        String publishedNoteListRedisKey = RedisKeyConstants.buildPublishedNoteListKey(creatorId);
        redisTemplate.delete(publishedNoteListRedisKey);

        try {
            // 笔记入库存储
            noteDOMapper.insert(noteDO);
        } catch (Exception e) {
            log.error("==> 笔记存储失败", e);
            // RPC: 笔记保存失败，则删除cassandra中的笔记内容
            if (StringUtils.isNotBlank(contentUuid)) {
                keyValueRpcService.deleteNoteContent(contentUuid);
            }
        }

        // 第二次延迟双删：发送延迟消息 删除个人主页 - 已发布笔记列表缓存
        sendDelayDeleteRedisPublishedNoteListCacheMQ(creatorId);

        //通知MQ计数服务，在redis中用户维度Hash以及数据库中统计笔记总数
        //构建用于传输数据的DTO
        NoteOperateMqDTO noteOperateMqDTO=NoteOperateMqDTO.builder()
                .creatorId(creatorId)
                .noteId(Long.valueOf(snowflakeIdId))
                .type(NoteOperateEnum.PUBLISH.getCode())
                .build();
        //构造String消息体
        Message<String> message=MessageBuilder
                .withPayload(JsonUtils.toJsonString(noteOperateMqDTO))
                .build();
        //构造主题与标签
        String destination = MQConstants.TOPIC_NOTE_OPERATE + ":" + MQConstants.TAG_NOTE_PUBLISH;
        //发送消息
        rocketMQTemplate.asyncSend(destination, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【笔记发布】MQ 发送成功，SendResult: {}", sendResult);
            }
            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【笔记发布】MQ 发送异常: ", throwable);
            }
        });
        return Response.success();
    }

    /**
     * 根据笔记id查询笔记详情
     * @param findNoteDetailReqVO
     * @return
     */
    @Override
    @SneakyThrows
    // @SneakyThrows它允许方法抛出检查型异常而无需显式声明或捕获这些异常。
    // 这对于那些不希望在方法签名中声明异常或不愿意编写复杂的 `try-catch` 块的场景非常有用。
    public Response<FindNoteDetailRspVO> findNoteDetail(FindNoteDetailReqVO findNoteDetailReqVO)
    {
        // 查询的笔记 ID
        Long noteId = findNoteDetailReqVO.getId();
        // 当前登录用户
        Long userId = LoginUserContextHolder.getUserId();
        // 先从本地缓存中查询
        String findNoteDetailRspVOStrLocalCache = LOCAL_CACHE.getIfPresent(noteId);
        if (StringUtils.isNotBlank(findNoteDetailRspVOStrLocalCache)) {
            FindNoteDetailRspVO findNoteDetailRspVO =
                    JsonUtils.parseObject(findNoteDetailRspVOStrLocalCache, FindNoteDetailRspVO.class);
            log.info("==> 命中了本地缓存；{}", findNoteDetailRspVOStrLocalCache);
            // 可见性校验
            checkNoteVisibleFromVO(userId, findNoteDetailRspVO);
            return Response.success(findNoteDetailRspVO);
        }
        //构建Redis查询key
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        String noteDetailJson = redisTemplate.opsForValue().get(noteDetailRedisKey);
        if (StringUtils.isNotBlank(noteDetailJson)) {
            // redis缓存命中，则直接返回
            FindNoteDetailRspVO findNoteDetailRspVO =
                    JsonUtils.parseObject(noteDetailJson, FindNoteDetailRspVO.class);
            // 异步线程中将用户信息存入本地缓存
            threadPoolTaskExecutor.submit(() -> {
                // 写入本地缓存
                if (Objects.nonNull(findNoteDetailRspVO)) {
                    LOCAL_CACHE.put(noteId, JsonUtils.toJsonString(findNoteDetailRspVO));
                }
            });
            // 可见性校验
            checkNoteVisibleFromVO(userId, findNoteDetailRspVO);
            log.info("==> 命中了Redis缓存；{}", findNoteDetailRspVO);

            return Response.success(findNoteDetailRspVO);
        }
        // 若 Redis 缓存中获取不到，则走数据库查询笔记
        NoteDO noteDO = noteDOMapper.selectByPrimaryKey(noteId);
        // 若该笔记不存在，则抛出业务异常
        if (Objects.isNull(noteDO)) {
            threadPoolTaskExecutor.execute(() -> {
                // 防止缓存穿透，将空数据存入 Redis 缓存 (过期时间不宜设置过长)
                // 保底1分钟 + 随机秒数
                long expireSeconds = 60 + RandomUtil.randomInt(60);
                redisTemplate.opsForValue()
                        .set(noteDetailRedisKey, "null", expireSeconds, TimeUnit.SECONDS);
            });
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        // 可见性校验
        Integer visible = noteDO.getVisible();
        //若可见性设置仅自己可见, 并且访问用户（userId）为笔记创建者才能访问，非本人则抛出异常
        //具体见自定义方法
        checkNoteVisible(visible, userId, noteDO.getCreatorId());


        // RPC: 调用用户服务 得到笔记创建者信息（昵称、头像）
        Long creatorId = noteDO.getCreatorId();
        FindUserByIdRspDTO findUserByIdRspDTO = userRpcService.findById(creatorId);
        // RPC: 调用 K-V 存储服务获取cassandra内容
        // 若笔记内容不为空，则调用 K-V 存储服务获取笔记内容
        String content = null;
        if (Objects.equals(noteDO.getIsContentEmpty(), Boolean.FALSE)) {
            content = keyValueRpcService.findNoteContent(noteDO.getContentUuid());
        }
        // 笔记类型  若查询的笔记类型为图文模式，还需要将图片链接拆分开，转成集合；
        Integer noteType = noteDO.getType();
        // 图文笔记图片链接(字符串)
        String imgUrisStr = noteDO.getImgUris();
        // 图文笔记图片链接(集合)
        List<String> imgUris = null;
        // 如果查询的是图文笔记，需要将图片链接的逗号分隔开，转换成集合
        if (Objects.equals(noteType, NoteTypeEnum.IMAGE_TEXT.getCode())
                && StringUtils.isNotBlank(imgUrisStr)) {
            //imgUrisStr.split将一个以逗号分隔的字符串imgUrisStr分割为字符串数组，
            //List.of再将该数组转换为一个不可变的 List 集合。
            imgUris = List.of(imgUrisStr.split(","));
        }

        // 构建返参 VO 实体类
        FindNoteDetailRspVO findNoteDetailRspVO = FindNoteDetailRspVO.builder()
                .id(noteDO.getId())
                .type(noteDO.getType())
                .title(noteDO.getTitle())
                .content(content)
                .imgUris(imgUris)
                .videoUri(noteDO.getVideoUri())
                .topicId(noteDO.getTopicId())
                .topicName(noteDO.getTopicName())
                .creatorId(noteDO.getCreatorId())
                .creatorName(findUserByIdRspDTO.getNickName())
                .avatar(findUserByIdRspDTO.getAvatar())
                .updateTime(noteDO.getUpdateTime())
                .visible(noteDO.getVisible())
                .build();

        // 异步线程中将笔记详情存入 Redis
        threadPoolTaskExecutor.submit(() -> {
            String noteDetailJson1 = JsonUtils.toJsonString(findNoteDetailRspVO);
            // 过期时间（保底1天 + 随机秒数，将缓存过期时间打散，防止同一时间大量缓存失效, 导致缓存雪崩）
            long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
            redisTemplate.opsForValue()
                    .set(noteDetailRedisKey, noteDetailJson1, expireSeconds, TimeUnit.SECONDS);
        });
        return Response.success(findNoteDetailRspVO);
    }

    /**
     * 修改笔记
     * @param updateNoteReqVO
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<?> updateNote(UpdateNoteReqVO updateNoteReqVO) {
        // 笔记 ID
        Long noteId = updateNoteReqVO.getId();
        // 笔记类型
        Integer type = updateNoteReqVO.getType();
        // 获取对应类型的枚举
        NoteTypeEnum noteTypeEnum = NoteTypeEnum.valueOf(type);
        // 若非图文、视频，抛出业务业务异常
        if (Objects.isNull(noteTypeEnum)) {
            throw new BizException(ResponseCodeEnum.NOTE_TYPE_ERROR);
        }
        String imgUris = null;
        String videoUri = null;
        switch (noteTypeEnum) {
            case IMAGE_TEXT: // 图文笔记
                List<String> imgUriList = updateNoteReqVO.getImgUris();
                // 校验图片是否为空
                Preconditions.checkArgument
                        (CollUtil.isNotEmpty(imgUriList), "笔记图片不能为空");
                // 校验图片数量
                Preconditions.checkArgument
                        (imgUriList.size() <= 8, "笔记图片不能多于 8 张");

                imgUris = StringUtils.join(imgUriList, ",");
                break;
            case VIDEO: // 视频笔记
                videoUri = updateNoteReqVO.getVideoUri();
                // 校验视频链接是否为空
                Preconditions.checkArgument
                        (StringUtils.isNotBlank(videoUri), "笔记视频不能为空");
                break;
            default:
                break;
        }

        // 当前登录用户 ID
        Long currUserId = LoginUserContextHolder.getUserId();
        NoteDO selectNoteDO = noteDOMapper.selectByPrimaryKey(noteId);
        // 笔记不存在
        if (Objects.isNull(selectNoteDO)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        // 判断权限：非笔记发布者不允许更新笔记
        if (!Objects.equals(currUserId, selectNoteDO.getCreatorId())) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }

        // 话题
        Long topicId = updateNoteReqVO.getTopicId();
        String topicName = null;
        if (Objects.nonNull(topicId)) {
            topicName = topicDOMapper.selectNameByPrimaryKey(topicId);
            // 判断一下提交的话题, 是否是真实存在的
//            if (StringUtils.isBlank(topicName))
//                throw new BizException(ResponseCodeEnum.TOPIC_NOT_FOUND);
        }

        // 获取新内容
        String content = updateNoteReqVO.getContent();

        // cassandra笔记内容更新
        // 查询此篇笔记内容对应的 UUID
        NoteDO noteDO1 = noteDOMapper.selectByPrimaryKey(noteId);
        String contentUuid = noteDO1.getContentUuid();

        // 笔记内容是否更新成功
        boolean isUpdateContentSuccess = false;
        if (StringUtils.isBlank(content)) {
            // 若笔记内容为空，则删除 K-V 存储
            isUpdateContentSuccess = keyValueRpcService.deleteNoteContent(contentUuid);
        } else {
            // 若将无内容的笔记，更新为了有内容的笔记，需要重新生成 UUID
            contentUuid = StringUtils.isBlank(contentUuid) ?
                    UUID.randomUUID().toString() : contentUuid;
            // 调用 K-V 保存短文本
            isUpdateContentSuccess = keyValueRpcService.saveNoteContent(contentUuid, content);
        }

        // 如果更新失败，抛出业务异常，回滚事务
        if (!isUpdateContentSuccess) {
            throw new BizException(ResponseCodeEnum.NOTE_UPDATE_FAIL);
        }

        //延迟双删策略

        // 第一次删除 笔记详情Redis 缓存
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        redisTemplate.delete(noteDetailRedisKey);
        // 第一次删除用户主页列表笔记 Redis 缓存
        String publishedNoteListRedisKey = RedisKeyConstants.buildPublishedNoteListKey(currUserId);
        redisTemplate.delete(publishedNoteListRedisKey);

        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .isContentEmpty(StringUtils.isBlank(content))
                .imgUris(imgUris)
                .title(updateNoteReqVO.getTitle())
                .topicId(updateNoteReqVO.getTopicId())
                .topicName(topicName)
                .type(type)
                .updateTime(LocalDateTime.now())
                .videoUri(videoUri)
                .contentUuid(contentUuid)
                .build();
        //更新数据库
        noteDOMapper.updateByPrimaryKey(noteDO);

        // 第二次删除用户主页列表笔记 Redis 缓存 使用RocketMQ异步发送延时消息，延时1s删除
        sendDelayDeleteRedisPublishedNoteListCacheMQ(currUserId);
        // 第二次删除笔记详情redis缓存，使用RocketMQ异步发送延时消息，延时1s删除
        //构建消息体
        Message<String> message = MessageBuilder.withPayload(String.valueOf(noteId))
                .build();

        //asyncSend表示异步发送消息，返回SendCallback对象，表示发送成功或失败的回调。
        //五个参数分别为：Topic、Message、SendCallback、timeoutMillis、delayLevel
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_DELAY_DELETE_NOTE_REDIS_CACHE, message,
                new SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {
                        log.info("## 延时删除 Redis 笔记缓存消息发送成功...");
                    }

                    @Override
                    public void onException(Throwable e) {
                        log.error("## 延时删除 Redis 笔记缓存消息发送失败...", e);
                    }
                },
                3000, // 超时时间(毫秒)
                1 // 延迟级别，是用于控制消息延迟投递时间的关键参数。1 表示延时 1s
        );

        // 删除本地缓存,（这里只删除这一个实例的本地缓存，还有其他实例的缓存没有删除，要使用MQ）
        //LOCAL_CACHE.invalidate(noteId);
        // 在笔记更新成功时，发送广播消息，通知所有的笔记服务实例(集群)，立即完成各自对本地缓存的删除。
        //syncSend表示同步发送MQ消息、发送后会阻塞线程等待Broker确认消息已接收，确保消息不丢失
        //asyncSend表示异步发送MQ消息，发送后会不等待消费者确认消息已接收，可能丢失消息
        //主题是MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE  消息体是noteId
        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, String.valueOf(noteId));
        log.info("====> MQ：删除笔记本地缓存发送成功...");

        return Response.success();
    }

    /**
     * 删除笔记本地缓存，每个消费者（实例）都会调用此方法 来删除缓存
     * @param noteId
     */
    @Override
    public void deleteNoteLocalCache(Long noteId)
    {
        LOCAL_CACHE.invalidate(noteId);
    }

    /**
     * 删除笔记
     * @param deleteNoteReqVO
     * @return
     */
    @Override
    public Response<?> deleteNote(DeleteNoteReqVO deleteNoteReqVO) {
        //先拿到笔记id
        Long noteId = deleteNoteReqVO.getId();
        //拿到笔记详细信息
        NoteDO selectNoteDO = noteDOMapper.selectByPrimaryKey(noteId);
        // 判断笔记是否存在
        if (Objects.isNull(selectNoteDO)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        // 判断权限：非笔记发布者不允许删除笔记
        Long currUserId = LoginUserContextHolder.getUserId();
        if (!Objects.equals(currUserId, selectNoteDO.getCreatorId())) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }

        //删除笔记详情redis缓存（不需要延时双删）
        redisTemplate.delete(RedisKeyConstants.buildNoteDetailKey(noteId));
        //删除用户主页列表笔记 redis 缓存
        String publishedNoteListRedisKey = RedisKeyConstants.buildPublishedNoteListKey(currUserId);
        redisTemplate.delete(publishedNoteListRedisKey);
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .status(NoteStatusEnum.DELETED.getCode())
                .updateTime(LocalDateTime.now())
                .build();
        //这里是逻辑删除，并非是物理删除，所以只需要将该条笔记的status字段修改为2，即代表该篇笔记被删除了
        //更新数据库 ,count是影响的行数
        int count = noteDOMapper.updateByPrimaryKeySelective(noteDO);
        //若影响的行数为 0，则表示该笔记不存在
        if (count == 0) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        // 第二次延迟双删MQ发送删除用户主页笔记列表redis缓存
        sendDelayDeleteRedisPublishedNoteListCacheMQ(currUserId);

        //删除本地缓存 使用RocketMQ广播通知所有实例立即删除他们各自的本地缓存
        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, noteId);
        log.info("====> MQ：删除笔记本地缓存发送成功...");

        // 发送 MQ 通知计数服务 统计笔记数量
        // 构建消息体 DTO
        NoteOperateMqDTO noteOperateMqDTO = NoteOperateMqDTO.builder()
                .creatorId(selectNoteDO.getCreatorId())
                .noteId(noteId)
                .type(NoteOperateEnum.DELETE.getCode()) // 删除笔记
                .build();
        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message2 = MessageBuilder
                .withPayload(JsonUtils.toJsonString(noteOperateMqDTO))
                .build();
        // 通过冒号连接, 可让 MQ 发送给主题 Topic 时，携带上标签 Tag
        String destination = MQConstants.TOPIC_NOTE_OPERATE + ":" + MQConstants.TAG_NOTE_DELETE;

        // 异步发送 MQ 消息，提升接口响应速度
        rocketMQTemplate.asyncSend(destination, message2, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【笔记删除】MQ 发送成功，SendResult: {}", sendResult);
            }
            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【笔记删除】MQ 发送异常: ", throwable);
            }
        });

        return Response.success();
    }

    /**
     * 笔记仅自己可见
     * @param updateNoteVisibleOnlyMeReqVO
     * @return
     */
    @Override
    public Response<?> visibleOnlyMe(UpdateNoteVisibleOnlyMeReqVO updateNoteVisibleOnlyMeReqVO) {
        //先拿到笔记id
        Long noteId = updateNoteVisibleOnlyMeReqVO.getId();
        //拿到笔记详细信息
        NoteDO selectNoteDO = noteDOMapper.selectByPrimaryKey(noteId);
        // 判断笔记是否存在
        if (Objects.isNull(selectNoteDO)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        // 判断权限：非笔记发布者不允许修改笔记权限
        Long currUserId = LoginUserContextHolder.getUserId();
        if (!Objects.equals(currUserId, selectNoteDO.getCreatorId())) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }
        // 构建更新 DO 实体类
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .visible(NoteVisibleEnum.PRIVATE.getCode()) // 可见性设置为仅对自己可见
                .updateTime(LocalDateTime.now())
                .build();

        // 执行更新 SQL
        int count = noteDOMapper.updateVisibleOnlyMe(noteDO);
        // 若影响的行数为 0，则表示该笔记无法修改为仅自己可见
        if (count == 0) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_VISIBLE_ONLY_ME);
        }

        // 删除 Redis 缓存
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        redisTemplate.delete(noteDetailRedisKey);
        // 同步发送广播模式 MQ，将所有实例中的本地缓存都删除掉
        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, noteId);
        log.info("====> MQ：删除笔记本地缓存发送成功...");
        return Response.success();
    }

    /**
     * 置顶/取消置顶笔记
     * @param topNoteReqVO
     * @return
     */
    @Override
    public Response<?> topNote(TopNoteReqVO topNoteReqVO) {
        // 笔记 ID
        Long noteId = topNoteReqVO.getId();
        // 是否置顶
        Boolean isTop = topNoteReqVO.getIsTop();

        // 当前登录用户 ID
        Long currUserId = LoginUserContextHolder.getUserId();

        // 构建置顶/取消置顶 DO 实体类
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .isTop(isTop)
                .updateTime(LocalDateTime.now())
                .creatorId(currUserId) // 只有笔记所有者，才能置顶/取消置顶笔记
                .build();

        int count = noteDOMapper.updateIsTop(noteDO);

        if (count == 0) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }

        // 删除 Redis 缓存
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        redisTemplate.delete(noteDetailRedisKey);

        // 同步发送广播模式 MQ，将所有实例中的本地缓存都删除掉
        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, noteId);
        log.info("====> MQ：删除笔记本地缓存发送成功...");

        return Response.success();
    }

    /**
     * 校验笔记的可见性
     * @param visible    是否可见
     * @param currUserId 当前用户 ID
     * @param creatorId  笔记创建者
     */
    private void checkNoteVisible(Integer visible, Long currUserId, Long creatorId) {
        // 若可见性设置仅自己可见, 并且访问用户（userId）为笔记创建者才能访问，非本人则抛出异常
        if (Objects.equals(visible, NoteVisibleEnum.PRIVATE.getCode())
                && !Objects.equals(currUserId, creatorId)) {
            throw new BizException(ResponseCodeEnum.NOTE_PRIVATE);
        }
    }

    /**
     * 校验笔记的可见性（针对 VO 实体类）
     * @param userId
     * @param findNoteDetailRspVO
     */
    private void checkNoteVisibleFromVO(Long userId, FindNoteDetailRspVO findNoteDetailRspVO) {
        if (Objects.nonNull(findNoteDetailRspVO)) {
            Integer visible = findNoteDetailRspVO.getVisible();
            checkNoteVisible(visible, userId, findNoteDetailRspVO.getCreatorId());
        }
    }

    /**
     * 点赞笔记
     * @param likeNoteReqVO
     * @return
     */
    @Override
    public Response<?> likeNote(LikeNoteReqVO likeNoteReqVO) {
        // 笔记ID
        Long noteId = likeNoteReqVO.getId();
        // 1. 校验被点赞的笔记是否存在，不存在直接抛出异常，存在的话看看在哪里存在，
        //本地缓若存在则不管，redis存在则同步到本地缓存中，数据库存在则同步到redis中
        Long creatorId = checkNoteIsExistAndGetCreatorId(noteId);

         //2. 判断目标笔记，是否已经点赞过 ，没有点赞过就添加笔记id到该用户的咆哮位图中
        // 当前登录用户ID
        Long userId = LoginUserContextHolder.getUserId();
        // 用户点赞列表 ZSet Key
        String userNoteLikeZSetKey = RedisKeyConstants.buildUserNoteLikeZSetKey(userId);
//        // 布隆过滤器 Key  bloom:note:likes:userId
////布隆过滤器存储所有点赞笔记的 ID。在用户点赞时，首先查询这个过滤器是否已经包含目标笔记的 noteId。
//        String bloomUserNoteLikeListKey = RedisKeyConstants.buildBloomUserNoteLikeListKey(userId);
//        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
//        // Lua 脚本路径
//        script.setScriptSource(new ResourceScriptSource(
//                new ClassPathResource("/lua/bloom_note_like_check.lua")));
//        // 返回值类型
//        script.setResultType(Long.class);
//        // 执行 Lua 脚本，拿到返回结果
//        Long result = redisTemplate
//                .execute(script, Collections.singletonList(bloomUserNoteLikeListKey), noteId);

        // Roaring Bitmap Key
        String rbitmapUserNoteLikeListKey = RedisKeyConstants
                .buildRBitmapUserNoteLikeListKey(userId);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("/lua/rbitmap_note_like_check.lua")));
        // 返回值类型
        script.setResultType(Long.class);
        // 执行 Lua 脚本，拿到返回结果
        Long result = redisTemplate.execute
                (script, Collections.singletonList(rbitmapUserNoteLikeListKey), noteId);
        // Lua 脚本返回结果转换为枚举
        NoteLikeLuaResultEnum noteLikeLuaResultEnum = NoteLikeLuaResultEnum.valueOf(result);
        switch (noteLikeLuaResultEnum) {
            // Redis缓存中位图不存在
            case NOT_EXIST -> {
                // 从数据库中校验笔记是否被点赞，并异步初始化位图，设置过期时间
                int count = noteLikeDOMapper.selectCountByUserIdAndNoteId(userId, noteId);
                // 保底1天+随机秒数
                long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
                // 若目标笔记已经被点赞
                if (count > 0) {
                    // 异步线程初始化位图。同步当前用户所有点赞的笔记 ID 到位图中。
                    threadPoolTaskExecutor.submit(() ->
                            batchAddNoteLike2RBitmapAndExpire(userId, expireSeconds, rbitmapUserNoteLikeListKey));
                    throw new BizException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
                }
                // 设置Lua 脚本路径当查询数据库不存在点赞记录时，需要执行此脚本
                // 若目标笔记未被点赞，当前用户可能有点赞其他笔记，同步数据并初始化位图
                batchAddNoteLike2RBitmapAndExpire(userId, expireSeconds, rbitmapUserNoteLikeListKey);
                // 再将当前点赞的笔记 ID, 添加到位图中，并设置过期时间。方便以后校验
                script.setScriptSource(new ResourceScriptSource(
                        new ClassPathResource("/lua/rbitmap_add_note_like_and_expire.lua")));
                // 返回值类型
                script.setResultType(Long.class);
                redisTemplate.execute       //键是rbitmap:note:likes:userId  值是noteId
                        (script, Collections.singletonList(rbitmapUserNoteLikeListKey), noteId, expireSeconds);
            }
            // 目标笔记已经被点赞
//布隆过滤器在判断元素存在时，误判元素存在，事实上不存在。
//当布隆过滤器误判元素存在，需要进一步的校验。不存在的元素不会误判，所以不需要进一步校验，直接加入布隆过滤器中。
//采用Bloom 过滤器校验 + ZSet 校验 + 数据库校验
            case NOTE_LIKED ->{//元素存在
//                // 校验 ZSet 列表中是否包含被点赞的笔记ID
//                Double score = redisTemplate.opsForZSet().score(userNoteLikeZSetKey, noteId);
//                if (Objects.nonNull(score)) {   //Zset不为空，已点赞
//                     throw new BizException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
//                }
//                //  若 Score 为空，则表示 ZSet 点赞列表中不存在（可能是过期），查询数据库校验
//                int count = noteLikeDOMapper.selectNoteIsLiked(userId, noteId);
//                if (count > 0){//不为空，已点赞
//                    // 数据库里面有点赞记录，而 Redis 中 ZSet 不存在，需要重新异步初始化 ZSet
//                    asynInitUserNoteLikesZSet(userId, userNoteLikeZSetKey);
//                    throw new BizException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
//                }
                //位图中已点赞
                throw new BizException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
            }
        }
        // 3. 更新用户 ZSET 点赞列表
        LocalDateTime now = LocalDateTime.now();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("/lua/note_like_check_and_update_zset.lua")));
        // 返回值类型
        script.setResultType(Long.class);
        // 执行 Lua 脚本，拿到返回结果
        result = redisTemplate.execute(
                script, Collections.singletonList(userNoteLikeZSetKey), noteId, DateUtils.localDateTime2Timestamp(now));
        // 若 ZSet 列表不存在，需要从数据库中，查询出当前用户最新点赞的 100 篇笔记，并重新初始化 ZSet 列表。
        if (Objects.equals(result, NoteLikeLuaResultEnum.NOT_EXIST.getCode())) {
            // 查询当前用户最新点赞的 100 篇笔记
            List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper
                    .selectLikedByUserIdAndLimit(userId, 100);
            // 保底1天+随机秒数
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
            DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
            // Lua 脚本路径
            script2.setScriptSource(new ResourceScriptSource(
                    new ClassPathResource("/lua/batch_add_note_like_zset_and_expire.lua")));
            // 返回值类型
            script2.setResultType(Long.class);
            // 若数据库中存在点赞记录，需要批量同步
            if (CollUtil.isNotEmpty(noteLikeDOS)) {
                // 构建 Lua 参数
                Object[] luaArgs = buildNoteLikeZSetLuaArgs(noteLikeDOS, expireSeconds);
                //执行脚本 批量加入之前点赞的笔记 ID 到 ZSet 中
                redisTemplate
                        .execute(script2, Collections.singletonList(userNoteLikeZSetKey), luaArgs);
                // 再次调用 note_like_check_and_update_zset.lua 脚本，将新的点赞的笔记添加到 zset 中
                redisTemplate
                        .execute(script, Collections.singletonList(userNoteLikeZSetKey), noteId, DateUtils.localDateTime2Timestamp(now));
            } else {
                // 若数据库中，无点赞过的笔记记录，则直接将当前这条点赞的笔记 ID 添加到 ZSet 中，随机过期时间
                List<Object> luaArgs = Lists.newArrayList();
                luaArgs.add(DateUtils.localDateTime2Timestamp(LocalDateTime.now())); // score ：点赞时间戳
                luaArgs.add(noteId); // 当前点赞的笔记 ID
                luaArgs.add(expireSeconds); // 随机过期时间
                redisTemplate.execute(
                        script2, Collections.singletonList(userNoteLikeZSetKey), luaArgs.toArray());
            }
        }
        // 4. 发送 MQ, 将点赞数据落库
        // 构建消息体 DTO
        LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO = LikeUnlikeNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .type(LikeUnlikeNoteTypeEnum.LIKE.getCode()) // 点赞笔记
                .createTime(now)
                .noteCreatorId(creatorId)
                .build();
        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder
                .withPayload(JsonUtils.toJsonString(likeUnlikeNoteMqDTO))
                .build();
        // 构建主题, 可让 MQ 发送给主题 Topic 时，携带上标签 Tag
        String destination = MQConstants.TOPIC_LIKE_OR_UNLIKE + ":" + MQConstants.TAG_LIKE;
        //顺序执行
        String hashKey = String.valueOf(userId);
        // 异步发送 MQ 消息，提升接口响应速度
        rocketMQTemplate.asyncSendOrderly(destination, message, hashKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【笔记点赞】MQ 发送成功，SendResult: {}", sendResult);
            }
            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【笔记点赞】MQ 发送异常: ", throwable);
            }
        });
        return Response.success();
    }

    /**
     * 校验笔记是否存在并且返回笔记创建者id
     * @param noteId
     */
    private Long checkNoteIsExistAndGetCreatorId(Long noteId) {
        // 先从本地缓存校验
        String findNoteDetailRspVOStrLocalCache = LOCAL_CACHE.getIfPresent(noteId);
        // 解析 Json 字符串为 VO 对象
        FindNoteDetailRspVO findNoteDetailRspVO =
                JsonUtils.parseObject(findNoteDetailRspVOStrLocalCache, FindNoteDetailRspVO.class);
        // 若本地缓存没有
        if (Objects.isNull(findNoteDetailRspVO)) {
            // 再从 Redis 中校验
            String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
            String noteDetailJson = redisTemplate.opsForValue().get(noteDetailRedisKey);
            // 解析 Json 字符串为 VO 对象
            findNoteDetailRspVO = JsonUtils.parseObject(noteDetailJson, FindNoteDetailRspVO.class);
            // 都不存在，再查询数据库校验是否存在
            if (Objects.isNull(findNoteDetailRspVO)) {
                Long creatorId = noteDOMapper.selectCreatorIdByNoteId(noteId);
                // 若数据库中也不存在笔记创建者，提示用户笔记不存在
                if (Objects.isNull(creatorId)) {
                    throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
                }
                // 若数据库中存在，异步同步一下缓存
                //防止后续其他用户点赞时，校验笔记是否存在，流量都打到了数据库
                threadPoolTaskExecutor.submit(() -> {
                    FindNoteDetailReqVO findNoteDetailReqVO = FindNoteDetailReqVO
                            .builder()
                            .id(noteId)
                            .build();
                    //调用查看笔记详细信息接口，同步缓存，防止其他用户点赞时，校验笔记是否存在，流量都打到了数据库
                    findNoteDetail(findNoteDetailReqVO);
                });
                return creatorId;
            }
        }
        return findNoteDetailRspVO.getCreatorId();
    }

    /**
     * 异步初始化用户点赞笔记 ZSet
     * @param userId
     * @param userNoteLikeZSetKey
     */
    private void asynInitUserNoteLikesZSet(Long userId, String userNoteLikeZSetKey) {
        threadPoolTaskExecutor.execute(() -> {
            // 判断用户笔记点赞 ZSET 是否存在
            boolean hasKey = redisTemplate.hasKey(userNoteLikeZSetKey);
            // 不存在，则重新初始化
            if (!hasKey) {
                // 查询当前用户最新点赞的 100 篇笔记
                List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper
                        .selectLikedByUserIdAndLimit(userId, 100);
                if (CollUtil.isNotEmpty(noteLikeDOS)) {
                    // 保底1天+随机秒数
                    long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
                    // 构建 Lua 参数
                    Object[] luaArgs = buildNoteLikeZSetLuaArgs(noteLikeDOS, expireSeconds);
                    DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
                    // Lua 脚本路径
                    script2.setScriptSource(new ResourceScriptSource(
                            new ClassPathResource("/lua/batch_add_note_like_zset_and_expire.lua")));
                    // 返回值类型
                    script2.setResultType(Long.class);
                    redisTemplate.execute(
                            script2, Collections.singletonList(userNoteLikeZSetKey), luaArgs);
                }
            }
        });
    }

    /**
     * 异步批量添加笔记点赞数据初始化布隆过滤器
     * @param userId
     * @param expireSeconds
     * @param bloomUserNoteLikeListKey
     */
    private void batchAddNoteLike2BloomAndExpire(
            Long userId, long expireSeconds, String bloomUserNoteLikeListKey) {
        try {
            // 异步全量同步一下，并设置过期时间
            List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper.selectByUserId(userId);

            if (CollUtil.isNotEmpty(noteLikeDOS)) {
                DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                // Lua 脚本路径
                script.setScriptSource(new ResourceScriptSource(
                        new ClassPathResource("/lua/bloom_batch_add_note_like_and_expire.lua")));
                // 返回值类型
                script.setResultType(Long.class);
                // 构建 Lua 参数
                List<Object> luaArgs = Lists.newArrayList();
                noteLikeDOS.forEach(noteLikeDO -> luaArgs.add(noteLikeDO.getNoteId())); // 将每个点赞的笔记 ID 传入
                luaArgs.add(expireSeconds);  // 最后一个参数是过期时间（秒）
                redisTemplate.execute(
                        script, Collections.singletonList(bloomUserNoteLikeListKey), luaArgs.toArray());
            }
        } catch (Exception e) {
            log.error("## 异步初始化布隆过滤器异常: ", e);
        }
    }

    /**
     * 异步批量添加笔记点赞数据初始化Roaring Bitmap
     * @param userId
     * @param expireSeconds
     * @param rbitmapUserNoteLikeListKey
     */
    private void batchAddNoteLike2RBitmapAndExpire
            (Long userId, long expireSeconds, String rbitmapUserNoteLikeListKey) {
        try {
            // 异步全量同步一下，并设置过期时间
            List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper.selectByUserId(userId);

            if (CollUtil.isNotEmpty(noteLikeDOS)) {
                DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                // Lua 脚本路径
                script.setScriptSource(new ResourceScriptSource(
                        new ClassPathResource("/lua/rbitmap_batch_add_note_like_and_expire.lua")));
                // 返回值类型
                script.setResultType(Long.class);

                // 构建 Lua 参数
                List<Object> luaArgs = Lists.newArrayList();
                noteLikeDOS.forEach(noteLikeDO -> luaArgs.add(noteLikeDO.getNoteId())); // 将每个点赞的笔记 ID 传入
                luaArgs.add(expireSeconds);  // 最后一个参数是过期时间（秒）
                redisTemplate.execute(script, Collections.singletonList(rbitmapUserNoteLikeListKey), luaArgs.toArray());
            }
        } catch (Exception e) {
            log.error("## 异步初始化【笔记点赞】Roaring Bitmap 异常: ", e);
        }
    }

    /**
     * 构建初始化用户点赞ZSET Lua 脚本参数
     * @param noteLikeDOS
     * @param expireSeconds
     * @return
     */
    private static Object[] buildNoteLikeZSetLuaArgs
    (List<NoteLikeDO> noteLikeDOS, long expireSeconds) {// 每个笔记点赞关系有 2 个参数（score 和 value），最后再跟一个过期时间
        int argsLength = noteLikeDOS.size() * 2 + 1;
        Object[] luaArgs = new Object[argsLength];

        int i = 0;
        for (NoteLikeDO noteLikeDO : noteLikeDOS) {
            // 点赞时间作为 score
            luaArgs[i] = DateUtils.localDateTime2Timestamp(noteLikeDO.getCreateTime());
            // 笔记ID 作为 ZSet value
            luaArgs[i + 1] = noteLikeDO.getNoteId();
            i += 2;
        }
        // 最后一个参数是 ZSet 的过期时间
        luaArgs[argsLength - 1] = expireSeconds;
        return luaArgs;
    }

    /**
     * 取消点赞笔记
     * @param unlikeNoteReqVO
     * @return
     */
    @Override
    public Response<?> unlikeNote(UnlikeNoteReqVO unlikeNoteReqVO) {
        // 笔记ID
        Long noteId = unlikeNoteReqVO.getId();
        // 1. 校验笔记是否真实存在
        Long creatorId = checkNoteIsExistAndGetCreatorId(noteId);
        // 2. 校验笔记是否被点赞过
        // 当前登录用户ID
        Long userId = LoginUserContextHolder.getUserId();
//        // 构建布隆过滤器 Key  bloom:note:likes:userid
//        String bloomUserNoteLikeListKey = RedisKeyConstants.buildBloomUserNoteLikeListKey(userId);
//        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
//        // Lua 脚本路径
//        script.setScriptSource(
//                new ResourceScriptSource(new ClassPathResource("/lua/bloom_note_unlike_check.lua")));
//        // 返回值类型
//        script.setResultType(Long.class);
//        // 执行 Lua 脚本，拿到返回结果
//        Long result = redisTemplate
//                .execute(script, Collections.singletonList(bloomUserNoteLikeListKey), noteId);
//        // Lua 脚本返回结果转化为枚举

        // Roaring Bitmap Key
        String rbitmapUserNoteLikeListKey = RedisKeyConstants
                .buildRBitmapUserNoteLikeListKey(userId);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("/lua/rbitmap_note_unlike_check.lua")));
        // 返回值类型
        script.setResultType(Long.class);
        // 执行 Lua 脚本，拿到返回结果
        Long result = redisTemplate
                .execute(script, Collections.singletonList(rbitmapUserNoteLikeListKey), noteId);
        // Lua 脚本返回结果转化为枚举
        NoteUnlikeLuaResultEnum noteUnlikeLuaResultEnum = NoteUnlikeLuaResultEnum.valueOf(result);
        switch (noteUnlikeLuaResultEnum) { // 位图不存在
            case NOT_EXIST -> {
                // 异步初始化位图
                threadPoolTaskExecutor.submit(() -> {
                    // 保底1天+随机秒数
                    long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
                    // 批量向位图插入当前用户已点赞笔记id
                    batchAddNoteLike2BloomAndExpire(userId, expireSeconds, rbitmapUserNoteLikeListKey);
                });
                // 从数据库中校验笔记是否被点赞
                int count = noteLikeDOMapper.selectCountByUserIdAndNoteId(userId, noteId);
                // 未点赞，无法取消点赞操作，抛出业务异常
                if (count == 0) throw new BizException(ResponseCodeEnum.NOTE_NOT_LIKED);
            }
            // 位图校验目标笔记未被点赞，无法取消点赞
            case NOTE_NOT_LIKED -> throw new BizException(ResponseCodeEnum.NOTE_NOT_LIKED);
        }
        //3.删除 ZSET 中已点赞的笔记 ID
        String userNoteLikeZSetKey = RedisKeyConstants.buildUserNoteLikeZSetKey(userId);
        redisTemplate.opsForZSet().remove(userNoteLikeZSetKey, noteId);
        // 4. 发送 MQ, 数据更新落库
        //构造MQ消息体 DTO
        LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO = LikeUnlikeNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .type(LikeUnlikeNoteTypeEnum.UNLIKE.getCode())
                .createTime(LocalDateTime.now())
                .noteCreatorId(creatorId)
                .build();
        //对象转化为字符串，加入到message中
        Message<String> message =
                MessageBuilder.withPayload(JsonUtils.toJsonString(likeUnlikeNoteMqDTO)).build();
        //构建主题 携带上标签 Tag
        String destination = MQConstants.TOPIC_LIKE_OR_UNLIKE + ":" + MQConstants.TAG_UNLIKE;
        //带上 mq 执行顺序
        String hashKey = String.valueOf(userId);

        // 异步发送 MQ 消息，提升接口响应速度
        rocketMQTemplate.asyncSendOrderly(destination, message, hashKey, new SendCallback() {
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【笔记取消点赞】MQ 发送成功，SendResult: {}", sendResult);
            }

            public void onException(Throwable throwable){
                log.error("==> 【笔记取消点赞】MQ 发送异常: ", throwable);
            }
        });
        return Response.success();
    }

    /**
     * 收藏笔记
     * @param collectNoteReqVO
     * @return
     */
    @Override
    public Response<?> collectNote(CollectNoteReqVO collectNoteReqVO) {
        //得到笔记id
        Long noteId = collectNoteReqVO.getId();
        //1. 校验笔记是否真实存在
        Long creatorId = checkNoteIsExistAndGetCreatorId(noteId);
        //2. 用位图校验笔记是否被收藏过
        // 当前登录用户ID
        Long userId = LoginUserContextHolder.getUserId();
//        // 构建布隆过滤器 Key   bloom:note:collects:userId
//        String bloomUserNoteCollectListKey = RedisKeyConstants.buildBloomUserNoteCollectListKey(userId);
//        DefaultRedisScript<Long> script=new DefaultRedisScript<>();
//        script.setScriptSource(new ResourceScriptSource(
//                new ClassPathResource("/lua/bloom_note_collect_check.lua")));
//        // 设置返回值类型
//        script.setResultType(Long.class);
//        //执行Lua脚本，拿到返回结果
//        Long result = redisTemplate.execute(
//                script, Collections.singletonList(bloomUserNoteCollectListKey), noteId);
        //转化为枚举类型
        // Roaring Bitmap Key
        String rbitmapUserNoteCollectListKey = RedisKeyConstants
                .buildRBitmapUserNoteCollectListKey(userId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("/lua/rbitmap_note_collect_check.lua")));
        // 返回值类型
        script.setResultType(Long.class);
        // 执行 Lua 脚本，拿到返回结果
        Long result = redisTemplate.execute
                (script, Collections.singletonList(rbitmapUserNoteCollectListKey), noteId);
        // Lua 脚本返回结果转化为枚举
        NoteCollectLuaResultEnum noteCollectLuaResultEnum = NoteCollectLuaResultEnum.valueOf(result);
        // 构建 Redis Key 用户收藏列表
        String userNoteCollectZSetKey = RedisKeyConstants.buildUserNoteCollectZSetKey(userId);
        switch(noteCollectLuaResultEnum){
            case NOT_EXIST -> {
                // 笔记收藏位图不存在 要初始化位图 批量加入当前用户已收藏笔记id
                //查询当前笔记是否被收藏
                int count = noteCollectionDOMapper.selectCountByUserIdAndNoteId(userId, noteId);
                // 保底1天+随机秒数
                long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
                if(count>0){    //已被收藏
                    threadPoolTaskExecutor.submit(() ->
                            batchAddNoteCollect2RBitmapAndExpire(userId, expireSeconds, rbitmapUserNoteCollectListKey));
                    throw new BizException(ResponseCodeEnum.NOTE_ALREADY_COLLECTED);
                }
                // 若目标笔记未被收藏，查询当前用户是否有收藏其他笔记，有则同步初始化位图
                batchAddNoteCollect2RBitmapAndExpire(userId, expireSeconds, rbitmapUserNoteCollectListKey);
                //再次添加当前笔记到位图
                // Lua 脚本路径
                script.setScriptSource(new ResourceScriptSource(
                        new ClassPathResource("/lua/rbitmap_add_note_collect_and_expire.lua")));
                // 返回值类型
                script.setResultType(Long.class);
                //执行脚本
                redisTemplate.execute(script, Collections.singletonList(rbitmapUserNoteCollectListKey), noteId, expireSeconds);
            }
            // 目标笔记已经被收藏
            case NOTE_COLLECTED -> {
//                // 校验用户收藏Zset列表是否存在该笔记
//                Double score = redisTemplate.opsForZSet().score(userNoteCollectZSetKey, noteId);
//                if(Objects.nonNull(score)){     //收藏列表已经存在该笔记 抛出异常
//                    throw new BizException(ResponseCodeEnum.NOTE_ALREADY_COLLECTED);
//                }
//                //可能列表过期或列表不存在该笔记 去数据库中查询
//                int count = noteCollectionDOMapper.selectNoteIsCollected(userId, noteId);
//                if(count>0){    //数据库中存在该笔记
//                    // 数据库里面有收藏记录，而 Redis 中 ZSet 未初始化，需要重新异步初始化 ZSet
//                    asynInitUserNoteCollectsZSet(userId, userNoteCollectZSetKey);
//                    throw new BizException(ResponseCodeEnum.NOTE_ALREADY_COLLECTED);
//                }
                throw new BizException(ResponseCodeEnum.NOTE_ALREADY_COLLECTED);
            }
        }
        //3. 更新用户收藏列表
        LocalDateTime now = LocalDateTime.now();//获取当前时间，之后转为时间戳，作为score
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("/lua/note_collect_check_and_update_zset.lua")));
        // 返回值类型
        script.setResultType(Long.class);
        // 执行 Lua 脚本，拿到返回结果
        result = redisTemplate.execute(script, Collections.singletonList(userNoteCollectZSetKey), noteId, DateUtils.localDateTime2Timestamp(now));
        // 若 ZSet 列表不存在，需要重新初始化
        if (Objects.equals(result, NoteCollectLuaResultEnum.NOT_EXIST.getCode())) {
            // 查询当前用户最新收藏的 300 篇笔记
            List<NoteCollectionDO> noteCollectionDOS =
                    noteCollectionDOMapper.selectCollectedByUserIdAndLimit(userId, 300);
            // 保底1天+随机秒数
            long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
            DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
            // Lua 脚本路径
            script2.setScriptSource(new ResourceScriptSource(
                    new ClassPathResource("/lua/batch_add_note_collect_zset_and_expire.lua")));
            // 返回值类型
            script2.setResultType(Long.class);
            // 若数据库中存在历史收藏笔记，需要批量同步
            if (CollUtil.isNotEmpty(noteCollectionDOS)) {
                // 构建 Lua 参数
                Object[] luaArgs = buildNoteCollectZSetLuaArgs(noteCollectionDOS, expireSeconds);
                redisTemplate.execute(
                        script2, Collections.singletonList(userNoteCollectZSetKey), luaArgs);
                // 再次调用 note_collect_check_and_update_zset.lua 脚本，将当前收藏的笔记添加到 zset 中
                redisTemplate.execute(script, Collections.singletonList(userNoteCollectZSetKey), noteId, DateUtils.localDateTime2Timestamp(now));
            } else { // 若无历史收藏的笔记，则直接将当前收藏的笔记 ID 添加到 ZSet 中，随机过期时间
                List<Object> luaArgs = Lists.newArrayList();
                luaArgs.add(DateUtils.localDateTime2Timestamp(LocalDateTime.now())); // score：收藏时间戳
                luaArgs.add(noteId); // 当前收藏的笔记 ID
                luaArgs.add(expireSeconds); // 随机过期时间
                redisTemplate.execute(
                        script2, Collections.singletonList(userNoteCollectZSetKey), luaArgs.toArray());
            }
        }
        //4. 添加笔记收藏到数据库
        //构建消息体
        CollectUnCollectNoteMqDTO collectUnCollectNoteMqDTO = CollectUnCollectNoteMqDTO.builder()
                .noteId(noteId)
                .userId(userId)
                .createTime(now)
                .type(CollectUnCollectNoteTypeEnum.COLLECT.getCode())
                .noteCreatorId(creatorId)
                .build();
        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(collectUnCollectNoteMqDTO))
                .build();
        //构建主题
        String destination = MQConstants.TOPIC_COLLECT_OR_UN_COLLECT + ":" + MQConstants.TAG_COLLECT;
        //顺序发送消息
        String hashKey = String.valueOf(userId);
        //异步发送MQ
        rocketMQTemplate.asyncSendOrderly(destination, message, hashKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【笔记收藏】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【笔记收藏】MQ 发送异常: ", throwable);
            }
        });

        return Response.success();

    }

    /**
     * 初始化笔记收藏布隆过滤器
     * @param userId
     * @param expireSeconds
     * @param bloomUserNoteCollectListKey
     */
    private void batchAddNoteCollect2BloomAndExpire
    (Long userId, long expireSeconds, String bloomUserNoteCollectListKey) {
        try {
            // 异步全量同步一下，并设置过期时间
            List<NoteCollectionDO> noteCollectionDOS = noteCollectionDOMapper
                    .selectByUserId(userId);

            if (CollUtil.isNotEmpty(noteCollectionDOS)) {
                DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                // Lua 脚本路径
                script.setScriptSource(new ResourceScriptSource(
                        new ClassPathResource("/lua/bloom_batch_add_note_collect_and_expire.lua")));
                // 返回值类型
                script.setResultType(Long.class);

                // 构建 Lua 参数
                List<Object> luaArgs = Lists.newArrayList();
                noteCollectionDOS.forEach(
                        // 将每个收藏的笔记 ID 传入
                        noteCollectionDO -> luaArgs.add(noteCollectionDO.getNoteId()));
                luaArgs.add(expireSeconds);  // 最后一个参数是过期时间（秒）
                redisTemplate.execute(script,
                        Collections.singletonList(bloomUserNoteCollectListKey), luaArgs.toArray());
            }
        } catch (Exception e) {
            log.error("## 异步初始化【笔记收藏】布隆过滤器异常: ", e);
        }
    }

    /**
     * 初始化笔记收藏位图
     * @param userId
     * @param expireSeconds
     * @param rbitmapUserNoteCollectListKey
     */
    private void batchAddNoteCollect2RBitmapAndExpire
    (Long userId, long expireSeconds, String rbitmapUserNoteCollectListKey) {
        try {
            // 异步全量同步一下，并设置过期时间
            List<NoteCollectionDO> noteCollectionDOS = noteCollectionDOMapper
                    .selectByUserId(userId);

            if (CollUtil.isNotEmpty(noteCollectionDOS)) {
                DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                // Lua 脚本路径
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/rbitmap_batch_add_note_collect_and_expire.lua")));
                // 返回值类型
                script.setResultType(Long.class);

                // 构建 Lua 参数
                List<Object> luaArgs = Lists.newArrayList();
                noteCollectionDOS.forEach(noteCollectionDO -> luaArgs.add(noteCollectionDO.getNoteId())); // 将每个收藏的笔记 ID 传入
                luaArgs.add(expireSeconds);  // 最后一个参数是过期时间（秒）
                redisTemplate.execute(script, Collections.singletonList(rbitmapUserNoteCollectListKey), luaArgs.toArray());
            }
        } catch (Exception e) {
            log.error("## 异步初始化【笔记收藏】Roaring Bitmap 异常: ", e);
        }
    }

    /**
     * 异步初始化用户收藏笔记 ZSet
     * @param userId
     * @param userNoteCollectZSetKey
     */
    private void asynInitUserNoteCollectsZSet(Long userId, String userNoteCollectZSetKey) {
        threadPoolTaskExecutor.execute(() -> {
            // 判断用户笔记收藏 ZSET 是否存在
            boolean hasKey = redisTemplate.hasKey(userNoteCollectZSetKey);

            // 不存在，则重新初始化
            if (!hasKey) {
                // 查询当前用户最新收藏的 300 篇笔记
                List<NoteCollectionDO> noteCollectionDOS = noteCollectionDOMapper.selectCollectedByUserIdAndLimit(userId, 300);
                if (CollUtil.isNotEmpty(noteCollectionDOS)) {
                    // 保底1天+随机秒数
                    long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
                    // 构建 Lua 参数
                    Object[] luaArgs = buildNoteCollectZSetLuaArgs(noteCollectionDOS, expireSeconds);

                    DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
                    // Lua 脚本路径
                    script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/batch_add_note_collect_zset_and_expire.lua")));
                    // 返回值类型
                    script2.setResultType(Long.class);

                    redisTemplate.execute(script2, Collections.singletonList(userNoteCollectZSetKey), luaArgs);
                }
            }
        });
    }

    /**
     * 构建笔记收藏 ZSET Lua 脚本参数
     * @param noteCollectionDOS
     * @param expireSeconds
     * @return
     */
    private static Object[] buildNoteCollectZSetLuaArgs
    (List<NoteCollectionDO> noteCollectionDOS, long expireSeconds) {
        int argsLength = noteCollectionDOS.size() * 2 + 1; // 每个笔记收藏关系有 2 个参数（score 和 value），最后再跟一个过期时间
        Object[] luaArgs = new Object[argsLength];

        int i = 0;
        for (NoteCollectionDO noteCollectionDO : noteCollectionDOS) {
            luaArgs[i] = DateUtils.localDateTime2Timestamp(noteCollectionDO.getCreateTime()); // 收藏时间作为 score
            luaArgs[i + 1] = noteCollectionDO.getNoteId();          // 笔记ID 作为 ZSet value
            i += 2;
        }

        luaArgs[argsLength - 1] = expireSeconds; // 最后一个参数是 ZSet 的过期时间
        return luaArgs;
    }

    /**
     * 取消收藏笔记
     * @param unCollectNoteReqVO
     * @return
     */
    @Override
    public Response<?> unCollectNote(UnCollectNoteReqVO unCollectNoteReqVO) {
        //拿到笔记id
        Long noteId = unCollectNoteReqVO.getId();
        //1.校验笔记是否存在
        Long userId = LoginUserContextHolder.getUserId();
        Long creatorId = checkNoteIsExistAndGetCreatorId(noteId);
//        //2.从布隆过滤器判断笔记是否未点赞
//        //构建布隆过滤器key
//        String bloomUserNoteCollectListKey = RedisKeyConstants.buildBloomUserNoteCollectListKey(userId);
//        DefaultRedisScript<Long> script=new DefaultRedisScript<>();
//        //设置脚本
//        script.setResultType(Long.class);
//        script.setScriptSource(new ResourceScriptSource
//                (new ClassPathResource("/lua/bloom_note_uncollect_check.lua")));
//        //执行脚本
//        Long result = redisTemplate.execute(script, Collections.singletonList(bloomUserNoteCollectListKey), noteId);

        //2.从位图中判断笔记是否未收藏
        // Roaring Bitmap Key
        String rbitmapUserNoteCollectListKey = RedisKeyConstants
                .buildRBitmapUserNoteCollectListKey(userId);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("/lua/rbitmap_note_uncollect_check.lua")));
        // 返回值类型
        script.setResultType(Long.class);
        // 执行 Lua 脚本，拿到返回结果
        Long result = redisTemplate.execute
                (script, Collections.singletonList(rbitmapUserNoteCollectListKey), noteId);
        NoteUnCollectLuaResultEnum noteUnCollectLuaResultEnum = NoteUnCollectLuaResultEnum.valueOf(result);
        switch (noteUnCollectLuaResultEnum){
            case NOT_EXIST -> {
                //当校验到位图不存在时，异步初始化位图
                threadPoolTaskExecutor.submit(() -> {
                    // 保底1天+随机秒数
                    long expireSeconds = 60*60*24 + RandomUtil.randomInt(60*60*24);
                    batchAddNoteCollect2BloomAndExpire(userId, expireSeconds, rbitmapUserNoteCollectListKey);
                });
                // 从数据库中校验笔记是否被收藏
                int count = noteCollectionDOMapper.selectCountByUserIdAndNoteId(userId, noteId);
                // 未收藏，无法取消收藏操作，抛出业务异常
                if (count == 0) throw new BizException(ResponseCodeEnum.NOTE_NOT_COLLECTED);
            }
            //未收藏 无法取消收藏
            case NOTE_NOT_COLLECTED -> throw new BizException(ResponseCodeEnum.NOTE_NOT_COLLECTED);

        }
        // 3. 删除 ZSET 中已收藏的笔记 ID
        // 用户收藏列表 ZSet Key
        String userNoteCollectZSetKey = RedisKeyConstants.buildUserNoteCollectZSetKey(userId);
        redisTemplate.opsForZSet().remove(userNoteCollectZSetKey, noteId);
        // 4. 发送 MQ, 数据更新落库
        // 构建消息体 DTO
        CollectUnCollectNoteMqDTO unCollectNoteMqDTO = CollectUnCollectNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .type(CollectUnCollectNoteTypeEnum.UN_COLLECT.getCode()) // 取消收藏笔记
                .createTime(LocalDateTime.now())
                .noteCreatorId(creatorId)
                .build();

        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(unCollectNoteMqDTO))
                .build();

        // 构建主题, 可让 MQ 发送给主题 Topic 时，携带上标签 Tag
        String destination = MQConstants.TOPIC_COLLECT_OR_UN_COLLECT + ":" + MQConstants.TAG_UN_COLLECT;
        String hashKey = String.valueOf(userId);
        // 异步发送顺序 MQ 消息，提升接口响应速度
        rocketMQTemplate.asyncSendOrderly(destination, message, hashKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【笔记取消收藏】MQ 发送成功，SendResult: {}", sendResult);
            }
            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【笔记取消收藏】MQ 发送异常: ", throwable);
            }
        });
        return Response.success();
    }

    /**
     * 滚动查询用户主页笔记列表
     * @param findPublishedNoteListReqVO
     * @return
     */
    @Override
    public Response<FindPublishedNoteListRspVO> findPublishedNoteList(FindPublishedNoteListReqVO findPublishedNoteListReqVO)
    {
        // 目标用户ID
        Long userId = findPublishedNoteListReqVO.getUserId();
        // 游标
        Long cursor = findPublishedNoteListReqVO.getCursor();
        // 返参 VO
        FindPublishedNoteListRspVO findPublishedNoteListRspVO = null;

        //  1.优先查询缓存
        // 构建 Redis Key
        String publishedNoteListRedisKey = RedisKeyConstants.buildPublishedNoteListKey(userId);
        // 若游标为空，表示查询的是第一页
        if (Objects.isNull(cursor)) {
            String publishedNoteListJson = redisTemplate.opsForValue().get(publishedNoteListRedisKey);

            if (StringUtils.isNotBlank(publishedNoteListJson)) {
                try {
                    log.info("## 已发布笔记列表命中了 Redis 缓存...");
                    // Json 字符串转 VO 集合
                    List<NoteItemRspVO> noteItemRspVOS = JsonUtils
                            .parseList(publishedNoteListJson, NoteItemRspVO.class);
                    // 按笔记 ID 降序，最新发布的笔记排最前面
                    List<NoteItemRspVO> sortedList = noteItemRspVOS.stream()
                            .sorted(Comparator.comparing(NoteItemRspVO::getNoteId).reversed()).toList();

                    // 如果是博主本人，需要调用计数服务，获取最新的点赞数据
                    getAndSetLatestLikeTotalIfAuthor(userId, sortedList);

                    // 批量获取以及设置当前用户对笔记的点赞状态
                    batchGetAndSetNoteIsLiked(sortedList);

                    // 过滤出最早发布的笔记 ID，充当下一页的游标
                    Optional<Long> earliestNoteId = noteItemRspVOS.stream()
                            .map(NoteItemRspVO::getNoteId).min(Long::compareTo);
                    findPublishedNoteListRspVO = FindPublishedNoteListRspVO.builder()
                            .notes(sortedList)
                            .nextCursor(earliestNoteId.orElse(null))
                            .build();
                    return Response.success(findPublishedNoteListRspVO);
                } catch (Exception e) {
                    log.error("", e);
                }
            }
        }

        // 2.缓存无，则滚动分页查询数据库
        List<NoteDO> noteDOS = noteDOMapper.selectPublishedNoteListByUserIdAndCursor(userId, cursor);
        if (CollUtil.isNotEmpty(noteDOS)) {
            // 数据库DO对象列表 转笔记出参Rsp VO对象列表
            List<NoteItemRspVO> noteVOS = noteDOS
                    .stream()
                    .map(noteDO -> {
                        // 获取封面图片（第一张图片链接）
                        String cover = StringUtils.isNotBlank(noteDO.getImgUris()) ?
                                StringUtils.split(noteDO.getImgUris(), ",")[0] : null;

                        NoteItemRspVO noteItemRspVO = NoteItemRspVO.builder()
                                .noteId(noteDO.getId())
                                .type(noteDO.getType())
                                .creatorId(noteDO.getCreatorId())
                                .cover(cover)
                                .videoUri(noteDO.getVideoUri())
                                .title(noteDO.getTitle())
                                .isLiked(false)     //自己是否点赞过该笔记，默认为未点赞状态
                                .build();
                        return noteItemRspVO;
                    })
                    .toList();



            // 3.Feign 调用用户服务，获取目标用户的头像、昵称
            Optional<Long> creatorIdOptional = noteDOS.stream().map(NoteDO::getCreatorId).findAny();
            FindUserByIdRspDTO findUserByIdRspDTO = userRpcService.findById(creatorIdOptional.get());
            if (Objects.nonNull(findUserByIdRspDTO)) {
                // 循环 VO 集合，分别为每条笔记赋值用户头像、昵称
                noteVOS.forEach(noteItemRspVO -> {
                    noteItemRspVO.setAvatar(findUserByIdRspDTO.getAvatar());
                    noteItemRspVO.setNickname(findUserByIdRspDTO.getNickName());
                });
            }

            // 4.Feign RPC调用计数服务，批量获取笔记点赞数
            List<Long> noteIds = noteDOS.stream().map(NoteDO::getId).toList();
            List<FindNoteCountsByIdRspDTO> findNoteCountsByIdRspDTOS = countRpcService.findByNoteIds(noteIds);

            if (CollUtil.isNotEmpty(findNoteCountsByIdRspDTOS)) {
                // DTO 集合转 Map
                Map<Long, FindNoteCountsByIdRspDTO> noteIdAndDTOMap = findNoteCountsByIdRspDTOS.stream()
                        .collect(Collectors.toMap(FindNoteCountsByIdRspDTO::getNoteId, dto -> dto));

                // 循环设置 VO 集合，设置每篇笔记的点赞量
                noteVOS.forEach(noteItemRspVO -> {
                    Long currNoteId = noteItemRspVO.getNoteId();
                    FindNoteCountsByIdRspDTO findNoteCountsByIdRspDTO = noteIdAndDTOMap.get(currNoteId);
//                  noteItemRspVO.setLikeTotal((Objects.nonNull(findNoteCountsByIdRspDTO) && Objects.nonNull(findNoteCountsByIdRspDTO.getLikeTotal())) ?
//                        NumberUtils.formatNumberString(findNoteCountsByIdRspDTO.getLikeTotal()) : "0");
                    noteItemRspVO.setLikeTotal
                            (NumberUtils.formatNumberString(findNoteCountsByIdRspDTO.getLikeTotal()));
                });
            }

            // 批量获取以及设置当前用户对笔记的点赞状态
            batchGetAndSetNoteIsLiked(noteVOS);

            // 过滤出这一次查询最小的笔记 ID，（在下一批次中属于最近发布的），充当下一页的游标
//min(Long::compareTo)对流中的Long类型元素执行最小值查找，使用Long的compareTo方法作为比较器
            Optional<Long> earliestNoteId = noteDOS.stream().map(NoteDO::getId).min(Long::compareTo);
// Optional<T>是一个包装器，要么包裹着一个非 null的 T 类型值,比如Long；要么表示 “值不存在”
// 设计初衷是强制开发者处理空值场景
            findPublishedNoteListRspVO = FindPublishedNoteListRspVO.builder()
                    .notes(noteVOS)  //设置笔记列表数据
// orElse(null)是拆包Optional<Long>为Long（或null）、匹配nextCursor的Long类型要求；
//空值安全处理：当 Optional 为空时，返回null，避免抛出异常，同时让前端能识别 “无下一页”。
                    .nextCursor(earliestNoteId.orElse(null))    //下一次分页的游标起始
                    .build();

            // 同步第一页已发布笔记（20条）到 Redis
            if (Objects.isNull(cursor)) {
                syncFirstPagePublishedNoteList2Redis(noteVOS, publishedNoteListRedisKey);
            }

        }
        return Response.success(findPublishedNoteListRspVO);
    }

    /**
     * 同步第一页已发布笔记到 Redis
     * @param noteVOS
     * @param publishedNoteListRedisKey
     */
    private void syncFirstPagePublishedNoteList2Redis(List<NoteItemRspVO> noteVOS, String publishedNoteListRedisKey)
    {
        if (CollUtil.isEmpty(noteVOS)) return;
        // 异步同步缓存
        threadPoolTaskExecutor.submit(() -> {
            // 过期时间，一小时以内（保底30分钟+随机秒数）
            long expireSeconds = 60 * 30 + RandomUtil.randomInt(60 * 30);
            redisTemplate.opsForValue()
                    .set(publishedNoteListRedisKey, JsonUtils.toJsonString(noteVOS), expireSeconds, TimeUnit.SECONDS);
        });
    }

    /**
     * 发送延时删除 Redis 已发布用户主页笔记列表缓存消息
     * @param userId
     */
    private void sendDelayDeleteRedisPublishedNoteListCacheMQ(Long userId) {
        Message<String> message = MessageBuilder.withPayload(String.valueOf(userId))
                .build();

        rocketMQTemplate.asyncSend(MQConstants.TOPIC_DELAY_DELETE_PUBLISHED_NOTE_LIST_REDIS_CACHE, message,
                new SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {
                        log.info("## 延时删除 Redis 已发布笔记列表缓存消息发送成功...");
                    }

                    @Override
                    public void onException(Throwable e) {
                        log.error("## 延时删除 Redis 已发布笔记列表缓存消息发送失败...", e);
                    }
                },
                3000, // 超时时间
                1 // 延迟级别，1 表示延时 1s
        );
    }

    /**
     * 如果是用户本人，需要调用计数服务，获取最新的点赞数据
     * @param userId
     * @param sortedList
     */
    private void getAndSetLatestLikeTotalIfAuthor(Long userId, List<NoteItemRspVO> sortedList) {
        Long loginUserId = LoginUserContextHolder.getUserId();
        // 用户已登录，并且查询的是自己
        if (Objects.nonNull(loginUserId) && Objects.equals(loginUserId, userId)) {
            //构造笔记列表id单独调用计数服务，获取最新的点赞量。
            List<Long> noteIds = sortedList.stream().map(NoteItemRspVO::getNoteId).toList();
            List<FindNoteCountsByIdRspDTO> findNoteCountsByIdRspDTOS = countRpcService.findByNoteIds(noteIds);
            // 为排序后的 VO 列表对象单独设置笔记的点赞量，进行覆盖
            setVOListLikeTotal(sortedList, findNoteCountsByIdRspDTOS);
        }
    }

    /**
     * 设置 VO 集合中每篇笔记的点赞量
     * @param noteItemRspVOS
     * @param findNoteCountsByIdRspDTOS
     */
    private static void setVOListLikeTotal(List<NoteItemRspVO> noteItemRspVOS, List<FindNoteCountsByIdRspDTO> findNoteCountsByIdRspDTOS)
    {
        if (CollUtil.isNotEmpty(findNoteCountsByIdRspDTOS)) {
            // DTO 集合转 Map
            Map<Long, FindNoteCountsByIdRspDTO> noteIdAndDTOMap = findNoteCountsByIdRspDTOS.stream()
                    .collect(Collectors.toMap(FindNoteCountsByIdRspDTO::getNoteId, dto -> dto));

            // 循环设置 VO 集合，设置每篇笔记的点赞量，对原数据进行覆盖
            noteItemRspVOS.forEach(noteItemRspVO -> {
                Long currNoteId = noteItemRspVO.getNoteId();
                FindNoteCountsByIdRspDTO findNoteCountsByIdRspDTO = noteIdAndDTOMap.get(currNoteId);
//                noteItemRspVO.setLikeTotal((Objects.nonNull(findNoteCountsByIdRspDTO) && Objects.nonNull(findNoteCountsByIdRspDTO.getLikeTotal())) ?
//                        NumberUtils.formatNumberString(findNoteCountsByIdRspDTO.getLikeTotal()) : "0");
                noteItemRspVO.setLikeTotal
                        (NumberUtils.formatNumberString(findNoteCountsByIdRspDTO.getLikeTotal()));
            });
        }
    }

    /**
     * 批量获取以及设置当前用户对笔记的点赞状态
     * @param noteItemRspVOS
     */
    private void batchGetAndSetNoteIsLiked(List<NoteItemRspVO> noteItemRspVOS) {
        // 当前登录用户的 ID
        Long loginUserId = LoginUserContextHolder.getUserId();
        // 若用户已登录
        if (Objects.nonNull(loginUserId)) {
            // 提取所有需要获取点赞状态的笔记 ID
            List<Long> noteIds = noteItemRspVOS.stream().map(NoteItemRspVO::getNoteId).toList();
            // 构建 Roaring Bitmap Key
            String rbitmapUserNoteLikeListKey = RedisKeyConstants
                    .buildRBitmapUserNoteLikeListKey(loginUserId);

            DefaultRedisScript<List> script = new DefaultRedisScript<>();
            // Lua 脚本路径
            script.setScriptSource(new ResourceScriptSource(
                    new ClassPathResource("/lua/rbitmap_batch_get_note_liked.lua")));
            // 返回值类型
            script.setResultType(List.class);

            // 执行 Lua 脚本，拿到返回结果
            List<Long> results = redisTemplate.execute(
                    script, Collections.singletonList(rbitmapUserNoteLikeListKey), noteIds.toArray());

            // 若 Redis 中缓存不存在，下标 0 存放的标识为 -1
            Long hasKey = results.get(0);
            // 若 Roaring Bitmap 不存在
            if (Objects.equals(hasKey, NoteLikeLuaResultEnum.NOT_EXIST.getCode())) {
                // 从数据库查询
                List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper.selectByUserIdAndNoteIds(loginUserId, noteIds);

                if (CollUtil.isEmpty(noteLikeDOS)) return;

                // DO 转 Map, 方便查询对应笔记是否点赞
                Map<Long, NoteLikeDO> noteIdIsLikedMap = noteLikeDOS.stream()
                        .collect(Collectors.toMap(NoteLikeDO::getNoteId, notelikeDO -> notelikeDO));

                // 循环 VO 集合，设置是否点赞
                noteItemRspVOS.forEach(noteItemRspVO -> {
                    Long currNoteId = noteItemRspVO.getNoteId();
                    NoteLikeDO noteLikeDO = noteIdIsLikedMap.get(currNoteId);
                    if (Objects.nonNull(noteLikeDO)) noteItemRspVO.setIsLiked(true);
                });

                // 再异步初始化 Roaring Bitmap
                threadPoolTaskExecutor.submit(() -> {
                    // 随机过期时间（1小时内）
                    long expireSeconds = 60*30 + RandomUtil.randomInt(60*30);
                    batchAddNoteLike2RBitmapAndExpire(loginUserId, expireSeconds, rbitmapUserNoteLikeListKey);
                });
                return;
            }

            // 否则，则 Roaring Bitmap 存在
            // 初始化一个字典，解析 Lua 结果，并设置每篇笔记是否被点赞
            Map<Long, Boolean> likedMap = Maps.newHashMapWithExpectedSize(noteIds.size());
            for (int i = 0; i < noteIds.size(); i++) {
                Long currNoteId = noteIds.get(i);
                //result数组 1表示该笔记被当前用户点赞，0表示该笔记没有被当前用户点赞
                Boolean isLiked = Objects.equals(results.get(i), 1L);
                likedMap.put(currNoteId, isLiked);
            }

            // 循环 VO 集合，设置是否点赞
            noteItemRspVOS.forEach(noteItemRspVO -> {
                Long currNoteId = noteItemRspVO.getNoteId();
                noteItemRspVO.setIsLiked(likedMap.get(currNoteId));
            });
        }
    }


}
