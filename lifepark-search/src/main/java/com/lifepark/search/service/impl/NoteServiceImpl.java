package com.lifepark.search.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import com.lifepark.framework.common.constant.DateConstants;
import com.lifepark.framework.common.response.PageResponse;
import com.lifepark.framework.common.util.DateUtils;
import com.lifepark.framework.common.util.NumberUtils;
import com.lifepark.search.enums.NotePublishTimeRangeEnum;
import com.lifepark.search.enums.NoteSortTypeEnum;
import com.lifepark.search.index.NoteIndex;
import com.lifepark.search.model.vo.SearchNoteReqVO;
import com.lifepark.search.model.vo.SearchNoteRspVO;
import com.lifepark.search.service.NoteService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FieldValueFactorFunctionBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class NoteServiceImpl implements NoteService {
    @Resource
    private RestHighLevelClient restHighLevelClient;

    /**
     * 搜索笔记
     * @param searchNoteReqVO
     * @return
     */
    @Override
    public PageResponse<SearchNoteRspVO> searchNote(SearchNoteReqVO searchNoteReqVO) {
        // 查询关键词
        String keyword = searchNoteReqVO.getKeyword();
        // 当前页码
        Integer pageNo = searchNoteReqVO.getPageNo();
        // 笔记类型（图文or视频）
        Integer type = searchNoteReqVO.getType();
        // 排序条件（不选择则为默认）
        Integer sort = searchNoteReqVO.getSort();
        // 发布时间范围（一天内，一周内，半年内等等）
        Integer publishTimeRange = searchNoteReqVO.getPublishTimeRange();
        // 构建 SearchRequest，指定要查询的索引(表)
        SearchRequest searchRequest = new SearchRequest(NoteIndex.NAME);
        // 创建查询条件构建器
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        // 创建查询条件,使用 function_score 自定义调整文档得分
        //POST /note/_search
        //{
        //  "query": {                  //1、指定查询关键词文本
        //    "function_score": {
        //      "query": {
        //        "multi_match": {
        //          "query": "壁纸",
        //          "fields": ["title^2", "topic"] title字段的匹配得分被加权为2倍，表示它比topic话题重要。
        //        }
        //      },
        //       "filter": [           //2、添加过滤条件（类型，日期）
        //        {
        //          "term": {
        //            "type": 0
        //          }
        //        },
        //        {
        //          "range": {
        //            "create_time": {
        //              "gte": "2024-06-07 16:06:47",
        //              "lte": "2024-12-07 16:06:47"
        //            }
        //          }
        //        }
        //      ]
        //      "functions": [
        //        {
        //          "field_value_factor": {
        //            "field": "like_total",
        //            "factor": 0.5,
        //            "modifier": "sqrt",
        //            "missing": 0
        //          }
        //        },
        //        {
        //          "field_value_factor": {
        //            "field": "collect_total",
        //            "factor": 0.3,
        //            "modifier": "sqrt",
        //            "missing": 0
        //          }
        //        },
        //        {
        //          "field_value_factor": {
        //            "field": "comment_total",
        //            "factor": 0.2,
        //            "modifier": "sqrt",
        //            "missing": 0
        //          }
        //        }
        //      ],
        //      "score_mode": "sum",
        //      "boost_mode": "sum"
        //    }
        //  },
        //  "sort": [
        //    {
        //      "_score": {         _score也可为创建日期，点赞量，评论量，收藏量等等
        //        "order": "desc"
        //      }
        //    }
        //  ],
        //  "from": 0,
        //  "size": 10,
        //  "highlight": {
        //    "fields": {
        //      "title": {
        //        "pre_tags": ["<strong>"],
        //        "post_tags": ["</strong>"]
        //      }
        //    }
        //  }
        //}

        // 创建布尔查询条件
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must(QueryBuilders.multiMatchQuery(keyword)
                .field(NoteIndex.FIELD_NOTE_TITLE, 2.0f)  // 手动设置笔记标题的权重值为 2.0
                .field(NoteIndex.FIELD_NOTE_TOPIC)               // 不设置，权重默认为 1.0
        );

        // 1.先勾选了笔记类型，添加过滤条件
        if (Objects.nonNull(type)) {    //保留笔记索引中 note_type 字段值等于 type 的文档。
            boolQueryBuilder.filter(QueryBuilders.termQuery(NoteIndex.FIELD_NOTE_TYPE, type));
        }
        // 2.再按发布时间范围过滤
        NotePublishTimeRangeEnum notePublishTimeRangeEnum =
                NotePublishTimeRangeEnum.valueOf(publishTimeRange);
        if (Objects.nonNull(notePublishTimeRangeEnum)) {
            // 结束时间
            String endTime = LocalDateTime.now().format(DateConstants.DATE_FORMAT_Y_M_D_H_M_S);
            // 开始时间
            String startTime = null;
            switch (notePublishTimeRangeEnum) {
                case DAY ->// 一天之前的时间
                        startTime = DateUtils.localDateTime2String(LocalDateTime.now().minusDays(1));
                case WEEK ->// 一周之前的时间
                        startTime = DateUtils.localDateTime2String(LocalDateTime.now().minusWeeks(1));
                case HALF_YEAR ->// 半年之前的时间
                        startTime = DateUtils.localDateTime2String(LocalDateTime.now().minusMonths(6));
            }
            // 设置时间范围
            if (StringUtils.isNoneBlank(startTime)) {
                //只保留创建时间在 startTime 到 endTime 之间的文档。
                boolQueryBuilder.filter(QueryBuilders
                        .rangeQuery(NoteIndex.FIELD_NOTE_CREATE_TIME)
                        .gte(startTime) // 大于等于
                        .lte(endTime) // 小于等于
                );
            }
        }
        // 3.最后排序条件（按最新、点赞、收藏、评论等等排序，否则自定义规则排序）
        NoteSortTypeEnum noteSortTypeEnum = NoteSortTypeEnum.valueOf(sort);
        // 设置排序
        // "sort": [
        //     {
        //       "_score": {        _score可为创建日期，点赞量，评论量，收藏量等等。默认为得分
        //         "order": "desc"
        //       }
        //     }
        //   ]
        if (Objects.nonNull(noteSortTypeEnum)) {
            switch (noteSortTypeEnum) {
                // 按笔记发布时间降序
                case LATEST -> sourceBuilder.sort(new FieldSortBuilder
                        (NoteIndex.FIELD_NOTE_CREATE_TIME).order(SortOrder.DESC));
                // 按笔记点赞量降序
                case MOST_LIKE -> sourceBuilder.sort(new FieldSortBuilder(
                        NoteIndex.FIELD_NOTE_LIKE_TOTAL).order(SortOrder.DESC));
                // 按评论量降序
                case MOST_COMMENT -> sourceBuilder.sort(new FieldSortBuilder(
                        NoteIndex.FIELD_NOTE_COMMENT_TOTAL).order(SortOrder.DESC));
                // 按收藏量降序
                case MOST_COLLECT -> sourceBuilder.sort(new FieldSortBuilder(
                        NoteIndex.FIELD_NOTE_COLLECT_TOTAL).order(SortOrder.DESC));
            }
            // 设置条件查询
            sourceBuilder.query(boolQueryBuilder);
        } else { // 用户没用设置排序条件，使用自定义综合排序，自定义评分，并按 _score 评分降序
            sourceBuilder.sort(new FieldSortBuilder("_score").order(SortOrder.DESC));

            // 创建 FilterFunctionBuilder 数组
            // "functions": [
            //         {
            //           "field_value_factor": {
            //             "field": "like_total",
            //             "factor": 0.5,
            //             "modifier": "sqrt",
            //             "missing": 0
            //           }
            //         },
            //         {
            //           "field_value_factor": {
            //             "field": "collect_total",
            //             "factor": 0.3,
            //             "modifier": "sqrt",
            //             "missing": 0
            //           }
            //         },
            //         {
            //           "field_value_factor": {
            //             "field": "comment_total",
            //             "factor": 0.2,
            //             "modifier": "sqrt",
            //             "missing": 0
            //           }
            //         }
            //       ],
            FunctionScoreQueryBuilder.FilterFunctionBuilder[] filterFunctionBuilders =
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                            // function 1 点赞分 = 0.5 * sqrt(点赞数)
                            new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                                    new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_LIKE_TOTAL)
                                            .factor(0.5f)
                                            .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                            .missing(0)
                            ),
                            // function 2  收藏分 = 0.3 * sqrt(收藏数)
                            new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                                    new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_COLLECT_TOTAL)
                                            .factor(0.3f)
                                            .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                            .missing(0)
                            ),
                            // function 3 评论分 = 0.2 * sqrt(评论数)
                            new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                                    new FieldValueFactorFunctionBuilder(NoteIndex.FIELD_NOTE_COMMENT_TOTAL)
                                            .factor(0.2f)
                                            .modifier(FieldValueFactorFunction.Modifier.SQRT)
                                            .missing(0)
                            )
                    };

            // 构建 function_score 查询
            // "score_mode": "sum",
            // "boost_mode": "sum"
            FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders
                    // 基础查询 + 评分函数数组
                    .functionScoreQuery(boolQueryBuilder, filterFunctionBuilders)
                    // 多个评分函数的结果如何组合 ： 相加
                    .scoreMode(FunctionScoreQuery.ScoreMode.SUM)
                    // 评分函数结果如何与基础查询结果（ _score 即关键词匹配度）组合 ： 相加
                    .boostMode(CombineFunction.SUM);

            // 设置条件查询
            sourceBuilder.query(functionScoreQueryBuilder);
        }

        // 设置分页，from 和 size
        int pageSize = 10; // 每页展示数据量
        int from = (pageNo - 1) * pageSize; // 偏移量
        sourceBuilder.from(from);
        sourceBuilder.size(pageSize);

        // 设置高亮字段
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field(NoteIndex.FIELD_NOTE_TITLE)
                .preTags("<strong>") // 设置包裹标签
                .postTags("</strong>");
        sourceBuilder.highlighter(highlightBuilder);

        // 将构建的查询条件设置到 SearchRequest 中
        searchRequest.source(sourceBuilder);

        // 返参 VO 集合
        List<SearchNoteRspVO> searchNoteRspVOS = null;
        // 总文档数，默认为 0
        long total = 0;
        try {
            log.info("==> SearchRequest: {}", searchRequest.source().toString());
            // 执行搜索
            SearchResponse searchResponse = restHighLevelClient
                    .search(searchRequest, RequestOptions.DEFAULT);

            // 处理搜索结果
            total = searchResponse.getHits().getTotalHits().value;
            log.info("==> 命中文档总数, hits: {}", total);

            searchNoteRspVOS = Lists.newArrayList();

            // 获取搜索命中的文档列表
            SearchHits hits = searchResponse.getHits();

            for (SearchHit hit : hits) {
                log.info("==> 文档数据: {}", hit.getSourceAsString());

                // 获取文档的所有字段（以 Map 的形式返回）
                Map<String, Object> sourceAsMap = hit.getSourceAsMap();

                // 提取特定字段值
                Long noteId = (Long) sourceAsMap.get(NoteIndex.FIELD_NOTE_ID);
                String cover = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_COVER);
                String title = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_TITLE);
                String avatar = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_AVATAR);
                String nickname = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_NICKNAME);
                Integer commentTotal = (Integer) sourceAsMap.get(NoteIndex.FIELD_NOTE_COMMENT_TOTAL);
                Integer collectTotal = (Integer) sourceAsMap.get(NoteIndex.FIELD_NOTE_COLLECT_TOTAL);
                String updateTimeStr = (String) sourceAsMap.get(NoteIndex.FIELD_NOTE_UPDATE_TIME);
                LocalDateTime updateTime = LocalDateTime.parse(updateTimeStr, DateConstants.DATE_FORMAT_Y_M_D_H_M_S);
                Integer likeTotal = (Integer) sourceAsMap.get(NoteIndex.FIELD_NOTE_LIKE_TOTAL);

                // 获取高亮字段
                String highlightedTitle = null;
                if (CollUtil.isNotEmpty(hit.getHighlightFields())
                        && hit.getHighlightFields().containsKey(NoteIndex.FIELD_NOTE_TITLE)) {
                    highlightedTitle = hit.getHighlightFields()
                            .get(NoteIndex.FIELD_NOTE_TITLE)
                            .fragments()[0]
                            .string();
                }

                // 构建 VO 实体类
                SearchNoteRspVO searchNoteRspVO = SearchNoteRspVO.builder()
                        .noteId(noteId)
                        .cover(cover)
                        .title(title)
                        .highlightTitle(highlightedTitle)
                        .avatar(avatar)
                        .nickname(nickname)
                        .updateTime(DateUtils.formatRelativeTime(updateTime))
                        .likeTotal(NumberUtils.formatNumberString(likeTotal))
                        .commentTotal(NumberUtils.formatNumberString(commentTotal))
                        .collectTotal(NumberUtils.formatNumberString(collectTotal))
                        .build();
                searchNoteRspVOS.add(searchNoteRspVO);
            }
        } catch (IOException e) {
            log.error("==> 查询 Elasticserach 异常: ", e);
        }

        return PageResponse.success(searchNoteRspVOS, pageNo, total);
    }
}
