package com.example.web.controller;

import com.example.exceptions.BizException;
import com.example.web.vo.ResultVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 客服模块全局异常处理。
 * 统一把业务异常转换成 ResultVo.fail(code, msg)，保证前端响应拦截器能拿到非 200 的 code 并 reject。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResultVo<?> handleBizException(BizException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return ResultVo.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResultVo<?> handleRuntimeException(RuntimeException e) {
        log.error("未捕获运行时异常: {}", e.getMessage(), e);
        return ResultVo.fail(-1, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResultVo<?> handleException(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        return ResultVo.fail(-1, "系统内部错误");
    }
}
