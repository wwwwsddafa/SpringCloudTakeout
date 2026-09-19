package com.example.configs;

import lombok.Data;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import io.minio.MinioClient;

/**
 * @program: cloud161
 * @description:
 * @author: zy
 * @create: 2026-08-16 16:13
 */
@Data
@Configuration
//@ConfigurationProperties(prefix="minio")
public class MinioConfig {

    @Value("${minio.url}")
    private String url;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.bucket}")
    private String bucket;

    @Bean
    public MinioClient minioClient(){
        return MinioClient
                .builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
    }
}