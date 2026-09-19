package com.example.web.handlers;

import com.example.exceptions.BizException;
import com.example.web.vo.ResultCode;
import com.example.web.vo.ResultVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResultVo handleBizException(BizException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return ResultVo.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResultVo handleException(Exception e) {
        log.error("系统异常", e);
        return ResultVo.fail(ResultCode.FAIL.getCode(), "系统繁忙，请稍后重试");
    }
}