package com.lifepark.kv.biz;

import com.lifepark.framework.common.util.JsonUtils;
import com.lifepark.kv.biz.domain.dataobject.NoteContentDO;
import com.lifepark.kv.biz.domain.repository.NoteContentRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;
import java.util.UUID;

@SpringBootTest
@Slf4j
class CassandraTests {

    @Resource
    private NoteContentRepository noteContentRepository;

    /**
     * 测试插入数据
     */
    @Test
    void testInsert() {
        NoteContentDO nodeContent = NoteContentDO.builder()
                .id(UUID.randomUUID())
                .content("代码测试笔记内容插入")
                .build();

        noteContentRepository.save(nodeContent);
    }
    /**
     * 测试修改数据
     */
    @Test
    void testUpdate() {
        NoteContentDO nodeContent = NoteContentDO.builder()
                .id(UUID.fromString("e67981bb-0171-46b4-a815-137ee9286676"))
                .content("代码测试笔记内容更新")
                .build();

        noteContentRepository.save(nodeContent);
    }

    /**
     * 测试查询数据
     */
    @Test
    void testSelect() {
        //UUID.fromString(),将字符串形式的 UUID转换为UUID对象
        Optional<NoteContentDO> optional = noteContentRepository
                .findById(UUID.fromString("e67981bb-0171-46b4-a815-137ee9286676"));
        optional.ifPresent(noteContentDO ->
                log.info("查询结果：{}", JsonUtils.toJsonString(noteContentDO)));
    }

    /**
     * 测试删除数据
     */
    @Test
    void testDelete() {
        noteContentRepository
                .deleteById(UUID.fromString("e67981bb-0171-46b4-a815-137ee9286676"));
    }


}
