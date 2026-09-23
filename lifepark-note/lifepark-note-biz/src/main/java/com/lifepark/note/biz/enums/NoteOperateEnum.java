package com.lifepark.note.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

//删除笔记、发布笔记的枚举类型

@Getter
@AllArgsConstructor
public enum NoteOperateEnum {
    // 笔记发布
    PUBLISH(1),
    // 笔记删除
    DELETE(0),
    ;

    private final Integer code;

}

