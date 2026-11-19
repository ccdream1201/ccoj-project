package com.cc.ccojbackend.common.exception;

import com.cc.ccojbackend.common.result.Result;
import com.cc.ccojbackend.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理请求体缺失或格式不正确
     * @param e
     * @return
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadableBody(
            HttpMessageNotReadableException e) {

        return Result.error(
                ResultCode.PARAMS_ERROR.getCode(),
                "请求体缺失或格式不正确"
        );
    }

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {

        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数异常
     *
     * @param e
     * @return
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(
            MethodArgumentNotValidException e) {

        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数错误");

        return Result.error(
                ResultCode.PARAMS_ERROR.getCode(),
                message
        );
    }

    /**
     * 处理未分类异常
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {

        log.error("请求处理发生异常", e);

        return Result.error(ResultCode.SYSTEM_ERROR);
    }
}
