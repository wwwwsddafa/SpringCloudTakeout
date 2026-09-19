package com.example.controllers;

import com.example.service.FileUploadDeduplicationService;
import com.example.utils.FileTypeValidator;
import com.example.web.vo.ResultCode;
import com.example.web.vo.ResultVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @program: cloud161
 * @description:
 * @author: zy
 * @create: 2026-08-16 16:14
 */
@RestController
@Log
@Tag(name = "文件上传", description = "支持将单文件，多文件上传到Minio的API，支持文件类型：图片、PDF、Word、Excel，并能去重.")
@RefreshScope
public class FileUploadController {

    private final FileUploadDeduplicationService fileUploadDeduplicationService;

    @Value("${service.dateFormater}")
    private String dateFormater;

    @RequestMapping(value="now", method= RequestMethod.GET     )
    @Operation(
            summary = "获取服务器当前时间",
            description = "获取服务器当前时间"
    )
    public ResultVo now() {
        //利用dateFormater完成对当前时间的格式化
        Date d=new Date();
        SimpleDateFormat df=new SimpleDateFormat(  dateFormater );
        return ResultVo.success(   df.format(  d )      );
    }



   @Value("${servlet.multipart.max-file-size}")
    private String maxFileSize;

    @Autowired
    public FileUploadController(FileUploadDeduplicationService fileUploadDeduplicationService) {
        this.fileUploadDeduplicationService = fileUploadDeduplicationService;
    }

    @RequestMapping(value = "/upload", method = RequestMethod.POST)
    @Operation(
            summary = "单文件或多文件上传",
            description = "可上传文件到Minio服务器"
    )
    public ResultVo upload(@RequestPart("uploadFiles") MultipartFile[] uploadFiles) {
        return doUpload(uploadFiles, "files");
    }

    @RequestMapping(value = "/upload/review", method = RequestMethod.POST)
    @Operation(
            summary = "评论图片上传",
            description = "上传评论图片到Minio服务器的review目录"
    )
    public ResultVo uploadReview(@RequestPart("uploadFiles") MultipartFile[] uploadFiles) {
        return doUpload(uploadFiles, "review");
    }

    private ResultVo doUpload(MultipartFile[] uploadFiles, String subFolder) {
        if (uploadFiles == null || uploadFiles.length == 0) {
            // HTTP 400
            return ResultVo.fail(ResultCode.FILE_EMPTY.getCode(), ResultCode.FILE_EMPTY.getMessage());
        }
        List<String> urls = new ArrayList<>();
            for( MultipartFile file: uploadFiles){
                if( file.isEmpty() ){
                    log.info( file.getName()+"为空文件，不允许上传" );
                    continue;
                }
                if( file.getSize()> DataSize.parse(maxFileSize).toBytes()){
                    log.info("文件大小"+file.getSize()+"超过最大上传大小"+maxFileSize);
                    continue;
                }

            String contentType = file.getContentType();
            String originalFilename = file.getOriginalFilename(); //文件名
            if (!FileTypeValidator.isValidType(contentType, originalFilename)) {
                //注意：这里写法：只要有一个文件类型不被允许，就会返回错误信息，而不是继续上传其他文件
                return ResultVo.fail(ResultCode.FILE_TYPE_INVALID.getCode(), "文件类型不被允许：" + originalFilename + ". 只允许图片、PDF、Word、Excel格式.");
            }
                //检查文件的前几个字符魔术字.
                if (!FileTypeValidator.isValidType(file)) {
                    return ResultVo.fail(ResultCode.FILE_TYPE_INVALID.getCode(), ResultCode.FILE_TYPE_INVALID.getMessage());
                }

                try {
                    // Delegate file processing and deduplication to the service
                    String fileUrl = fileUploadDeduplicationService.processAndGetFileUrl(file, subFolder);
                    urls.add(fileUrl);
                } catch (Exception e) {
                    e.printStackTrace();
                    log.severe("上传文件到Minio失败: " + originalFilename);
                }
        }
        if (urls.size() > 0) {
            log.info("上传到Minio成功,多个图片的访问地址为:" + urls);
            return ResultVo.success(urls);
        }
        return ResultVo.fail(ResultCode.FAIL.getCode(), ResultCode.FAIL.getMessage());
    }
}