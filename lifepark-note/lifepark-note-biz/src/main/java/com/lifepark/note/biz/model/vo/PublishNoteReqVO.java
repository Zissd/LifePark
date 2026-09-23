package com.lifepark.note.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

//笔记发布

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PublishNoteReqVO {

//    {
//        "type": 0, // 笔记类型，0 代表图文笔记，1 代表视频笔记
//        "imgUris": ["http://127.0.0.1:9000/lifepark/example.jpg"],
// 图片链接数组，当为图文笔记时，此字段不能为空
//         "videoUri": "http://xxxx",       // 视频连接，当为视频笔记时，此字段不能为空
//         "title": "图文笔记测试标题",     // 笔记标题
//         "content": "图文笔记测试内容", // 笔记内容（可不填）
//         "topicId": 1                 // 话题 ID（可不填）
//    }
    @NotNull(message = "笔记类型不能为空")
    private Integer type;

    private List<String> imgUris;

    private String videoUri;

    private String title;

    private String content;

    private Long topicId;
}

