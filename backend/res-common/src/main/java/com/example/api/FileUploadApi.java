package com.example.api;

import com.example.web.vo.ResultVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 * @program: cloud161
 * @description: 用于访问文件上传服务的api接口
 * @author: zy
 * @create: 2026-08-17 15:07
 */
@FeignClient(name = "fileUpload") // 指向注册中心中的微服务名称
public interface FileUploadApi {

    @RequestMapping(value="upload", method= RequestMethod.POST , consumes= MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResultVo upload(@RequestPart("uploadFiles") MultipartFile[] uploadFiles);

    @RequestMapping(value="upload/review", method= RequestMethod.POST , consumes= MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResultVo uploadReview(@RequestPart("uploadFiles") MultipartFile[] uploadFiles);
}