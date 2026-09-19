package com.example.configs;

import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;

public class FeignMultipartSupportConfig {

    // Required for Feign to work with HttpMessageConverters (like for JSON)
    private final ObjectFactory<HttpMessageConverters> messageConverters;

    public FeignMultipartSupportConfig(ObjectFactory<HttpMessageConverters> messageConverters) {
        this.messageConverters = messageConverters;
    }

//这是一个 Feign 文件上传配置方法，用于让 OpenFeign 支持 multipart/form-data 格式的请求（即文件上传）。
    @Bean
    public Encoder feignFormEncoder() {
        // This combines Spring's encoders with the FormEncoder to handle multipart requests
        return new SpringFormEncoder(new SpringEncoder(messageConverters));
    }
}
/*
* SpringEncoder - Spring 原生的编码器，负责处理 JSON、表单等常规请求体的序列化
SpringFormEncoder - 扩展自 SpringEncoder，专门处理 multipart/form-data 格式（即文件上传）
*
* */