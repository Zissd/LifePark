package com.lifepark.kv.biz.domain.repository;

import com.lifepark.kv.biz.domain.dataobject.CommentContentDO;
import com.lifepark.kv.biz.domain.dataobject.CommentContentPrimaryKey;
import org.springframework.data.cassandra.repository.CassandraRepository;

import java.util.List;
import java.util.UUID;

//CassandraRepository: 这是 Spring Data Cassandra 提供的一个泛型接口
//它为 Cassandra 数据库提供了 CRUD（创建、读取、更新、删除）和其他一些基本的操作方法。
public interface CommentContentRepository extends CassandraRepository<CommentContentDO, CommentContentPrimaryKey> {


//Spring Data Cassandra 会解析你方法名中的关键词（比如findBy、deleteBy、And、In等），
// 以及实体类中主键（PrimaryKey）的字段名（noteId、yearMonth、contentId），
// 自动推断出对应的 CQL（Cassandra 查询语言）。

//    findBy：表示这是查询操作。
//    PrimaryKeyNoteId：匹配实体类CommentContentDO主键（CommentContentPrimaryKey）中的noteId字段。
//    And：多条件连接符。
//    PrimaryKeyYearMonthIn：匹配主键中的yearMonth字段，且用IN条件查询。
//    PrimaryKeyContentIdIn：匹配主键中的contentId字段，且用IN条件查询。
    /**
     * 批量查询评论内容
     * @param noteId
     * @param yearMonths
     * @param contentIds
     * @return
     */
    List<CommentContentDO> findByPrimaryKeyNoteIdAndPrimaryKeyYearMonthInAndPrimaryKeyContentIdIn(
            Long noteId, List<String> yearMonths, List<UUID> contentIds);


    /**
     * 删除评论正文
     * @param noteId
     * @param yearMonth
     * @param contentId
     */
    void deleteByPrimaryKeyNoteIdAndPrimaryKeyYearMonthAndPrimaryKeyContentId(
            Long noteId, String yearMonth, UUID contentId);

}

