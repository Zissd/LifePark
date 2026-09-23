package com.lifepark.framework.common.exception;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

//BizException 自定义事务异常，用于全局异常处理器的捕捉
public class BizException extends RuntimeException {

    // 异常码
    private String errorCode;
    // 错误信息
    private String errorMessage;

    public BizException(BaseExceptionInterface baseExceptionInterface) {
        this.errorCode = baseExceptionInterface.getErrorCode();
        this.errorMessage = baseExceptionInterface.getErrorMessage();
    }
}

