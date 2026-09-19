package com.example.handles;

import com.example.exceptions.BizException;
import com.example.exceptions.MinioUploadException;
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

    @ExceptionHandler(MinioUploadException.class)
    public ResultVo<?> exception(MinioUploadException ex) {
        log.error("Minio上传异常: {}", ex.getMessage(), ex);
        return ResultVo.fail(ResultCode.FILE_UPLOAD_FAILED.getCode(), ResultCode.FILE_UPLOAD_FAILED.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResultVo<?> exception(Exception e) {
        log.error("fileupload系统异常: {}", e.getMessage(), e);
        return ResultVo.fail(-1, "系统内部错误");
    }
}