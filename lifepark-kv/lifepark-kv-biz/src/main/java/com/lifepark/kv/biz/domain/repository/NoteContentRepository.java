package com.lifepark.kv.biz.domain.repository;

import com.lifepark.kv.biz.domain.dataobject.NoteContentDO;
import org.springframework.data.cassandra.repository.CassandraRepository;

import java.util.UUID;

//CassandraRepository: 这是 Spring Data Cassandra 提供的一个泛型接口
// 它为 Cassandra 数据库提供了 CRUD（创建、读取、更新、删除）和其他一些基本的操作方法。
public interface NoteContentRepository extends CassandraRepository<NoteContentDO, UUID> {

}

