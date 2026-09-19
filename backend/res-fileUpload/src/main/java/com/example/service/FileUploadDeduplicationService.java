package com.example.service;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import com.example.configs.MinioConfig;
import com.example.exceptions.BizException;
import com.example.exceptions.MinioUploadException;
import com.example.web.vo.ResultCode;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.MinioException;
import lombok.extern.java.Log;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * @program: cloud161
 * @description: 带去重功能的文件上传
 * @author: zy
 * @create: 2026-08-16 16:15
 */
@Service
@Log
public class FileUploadDeduplicationService {
    private static final String REDIS_IMAGE_MD5_PREFIX="file:md5:"; // redis保存文件的md5码的前缀 → file:md5:xxxxxxx
    private static final long REDIS_KEY_TTL_DAYS=30*12; //在redis中存储的时间，单位是天

    @Autowired
    private MinioConfig minioConfig;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 上传文件（默认 files/ 目录）
     */
    public String processAndGetFileUrl(MultipartFile file) throws IOException, MinioException {
        return processAndGetFileUrl(file, "files");
    }

    /**
     * 上传文件到指定子目录
     */
    public String processAndGetFileUrl(MultipartFile file, String subFolder) throws IOException, MinioException {
        String md5Hash=""; //文件的MD5值
        try (InputStream is = file.getInputStream()) {
            md5Hash = DigestUtils.md5Hex(is); //工具类计算 md5码
        } catch (IOException e) {
            log.severe("Failed to calculate MD5 hash for file:"+ file.getName());
            throw new BizException(ResultCode.FILE_UPLOAD_FAILED.getCode(), "文件处理失败: " + file.getName());
        }

        //到redis中检查，是否有这MD5值
        String redisKey=REDIS_IMAGE_MD5_PREFIX+md5Hash;    // 键："file:md5:xxxx"
        String existingFileUrl = this.stringRedisTemplate.opsForValue().get( redisKey );
        //如果 existingFileUrl 不为空， 则说明重复了，原来上传过，直接返回重复文件的地址
        if (existingFileUrl != null && !existingFileUrl.isEmpty()) {
            log.info("Duplicate detected for MD5: "+ md5Hash+". Returning existing URL:"+existingFileUrl);
            stringRedisTemplate.expire(redisKey, REDIS_KEY_TTL_DAYS, TimeUnit.DAYS); //刷新过期时间
            return existingFileUrl; //返回重复文件的地址
        }
        log.info("New unique file detected for MD5: "+md5Hash+". Uploading to minio.");
        // 4. Upload to minio.(if not a duplicate)
        String fileExtension = "";
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        //新上传的文件 的文件名： md5+后缀名
        String objectKey = subFolder + "/" + md5Hash + fileExtension;
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucket())
                            .object(objectKey)
                            .stream(inputStream, file.getSize(), -1L)
                            .contentType(file.getContentType())
                            .build()
            );
        }catch (Exception ex) {
            log.severe("Failed to upload file to minio: "+ex.getMessage());
            throw new MinioUploadException(ex.getMessage());
        }
        
        // 构建并返回文件访问URL
        String fileUrl = minioConfig.getUrl() + "/" + minioConfig.getBucket() + "/" + objectKey;
        
        // 将MD5和URL保存到Redis，用于后续去重
        stringRedisTemplate.opsForValue().set(redisKey, fileUrl, REDIS_KEY_TTL_DAYS, TimeUnit.DAYS);
        
        log.info("File uploaded successfully. URL: " + fileUrl);
        return fileUrl;
        }
}