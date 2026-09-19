package com.example.web.exceotions;

import com.example.exceptions.BizException;
import com.example.exception.NetException;
import com.example.web.vo.ResultCode;
import com.example.web.vo.ResultVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResultVo<?> handleBizException(BizException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return ResultVo.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(NetException.class)
    public ResultVo<?> netException(NetException e) {
        log.error("网络异常: {}", e.getMessage());
        return ResultVo.fail(ResultCode.NET_ERROR.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResultVo<?> exception(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        return ResultVo.fail(-1, "系统内部错误");
    }
}