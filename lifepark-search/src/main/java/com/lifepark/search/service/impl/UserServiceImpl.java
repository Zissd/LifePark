package com.lifepark.search.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import com.lifepark.framework.common.response.PageResponse;
import com.lifepark.framework.common.util.NumberUtils;
import com.lifepark.search.index.UserIndex;
import com.lifepark.search.model.vo.SearchUserReqVO;
import com.lifepark.search.model.vo.SearchUserRspVO;
import com.lifepark.search.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

//RestHighLevelClient 是 Elasticsearch Java 客户端 提供的一种高级客户端，用于与 Elasticsearch 进行交互。
    @Resource
    private RestHighLevelClient restHighLevelClient;

    /**
     * 搜索用户
     * @param searchUserReqVO
     * @return
     */
    @Override
    public PageResponse<SearchUserRspVO> searchUser(SearchUserReqVO searchUserReqVO) {
        // 查询关键词（可能是小红书id）
        String keyword = searchUserReqVO.getKeyword();
        // 当前页码
        Integer pageNo = searchUserReqVO.getPageNo();
        // 构建 SearchRequest 指定索引(表) user
        SearchRequest searchRequest = new SearchRequest(UserIndex.NAME);
        // 构建查询内容
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        // 构建 multi_match 查询，查询 nickname 和 lifepark_id 字段
        // multi_match 用于在多个指定字段（nickname、lifepark_id）上执行相同的文本(query)搜索，
//        GET /user/_search
//        {
//            "query": {
//            "multi_match": {
//                "query": "lifepark_xxx",
//                "fields": ["nickname", "lifepark_id"]
//                 }
//             },
//            "sort": [
//            {
//                "fans_total": {
//                "order": "desc"
//                }
//            }
//           ],
//           "from": 0,
//           "size": 10
//        }
        sourceBuilder.query(QueryBuilders.multiMatchQuery
                //第一个参数是查询内容，后面参数是字段列表。在多个指定字段上执行相同的关键字搜索，
                (keyword, UserIndex.FIELD_USER_NICKNAME, UserIndex.FIELD_USER_LIFEPARK_ID));
        // 排序，按 fans_total 降序
        SortBuilder<?> sortBuilder = new FieldSortBuilder(UserIndex.FIELD_USER_FANS_TOTAL)
                .order(SortOrder.DESC);
        sourceBuilder.sort(sortBuilder);
        // 设置分页，from 和 size
        int pageSize = 10; // 每页展示数据量
        int from = (pageNo - 1) * pageSize; // 偏移量
        sourceBuilder.from(from);
        sourceBuilder.size(pageSize);

        // 设置高亮字段
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field(UserIndex.FIELD_USER_NICKNAME)
                .preTags("<strong>") // 设置包裹标签
                .postTags("</strong>");
        sourceBuilder.highlighter(highlightBuilder);

        // 将构建的查询条件设置到 SearchRequest 中
        searchRequest.source(sourceBuilder);

        // 返参 VO 集合
        List<SearchUserRspVO> searchUserRspVOS = null;
        // 总文档数，默认为 0
        long total = 0;
        try {
            log.info("==> SearchRequest: {}", searchRequest);
            // 通过客户端的 .search() 方法执行查询请求 得到回应
            //构建返参 VO 实体类集合，并返回分页响应数据。
            SearchResponse searchResponse = restHighLevelClient
                    .search(searchRequest, RequestOptions.DEFAULT);
            // 1.从 SearchResponse 响应类中，获取到文档总数
            total = searchResponse.getHits().getTotalHits().value;
            log.info("==> 命中文档总数, hits: {}", total);
            // 出参 VO 集合
            searchUserRspVOS = Lists.newArrayList();
            // 2.从 SearchResponse 响应类中，获取到文档数据，搜索命中的文档（行）列表
            SearchHits hits = searchResponse.getHits();
            for (SearchHit hit : hits) {
                log.info("==> 文档数据: {}", hit.getSourceAsString());
                // 获取文档的所有字段（以 Map 的形式返回）
                Map<String, Object> sourceAsMap = hit.getSourceAsMap();
                // 提取特定字段值
                Long userId = ((Number) sourceAsMap.get(UserIndex.FIELD_USER_ID)).longValue();
                String nickname = (String) sourceAsMap.get(UserIndex.FIELD_USER_NICKNAME);
                String avatar = (String) sourceAsMap.get(UserIndex.FIELD_USER_AVATAR);
                String lifeparkId = (String) sourceAsMap.get(UserIndex.FIELD_USER_LIFEPARK_ID);
                Integer noteTotal = (Integer) sourceAsMap.get(UserIndex.FIELD_USER_NOTE_TOTAL);
                Integer fansTotal = (Integer) sourceAsMap.get(UserIndex.FIELD_USER_FANS_TOTAL);

                // 获取高亮字段
                String highlightedNickname = null;
                if (CollUtil.isNotEmpty(hit.getHighlightFields())
                        && hit.getHighlightFields().containsKey(UserIndex.FIELD_USER_NICKNAME)) {
                    highlightedNickname = hit.getHighlightFields()
                                    .get(UserIndex.FIELD_USER_NICKNAME)
                                    .fragments()[0]
                                    .string();
                }
                // 构建 VO 实体类
                SearchUserRspVO searchUserRspVO = SearchUserRspVO.builder()
                        .userId(userId)
                        .nickname(nickname)
                        .avatar(avatar)
                        .lifeparkId(lifeparkId)
                        .noteTotal(noteTotal)
                        .fansTotal(NumberUtils.formatNumberString(fansTotal))
                        .highlightNickname(highlightedNickname)
                        .build();
                searchUserRspVOS.add(searchUserRspVO);
            }
        } catch (Exception e) {
            log.error("==> 查询 Elasticserach 异常: ", e);
        }
        return PageResponse.success(searchUserRspVOS, pageNo, total);
    }
}
