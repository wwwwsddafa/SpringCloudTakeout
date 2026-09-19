package com.example.configs;


import feign.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @program: cloud161
 * @description: 服务治理配置
 * @author: zy
 * @create: 2026-08-17 15:50
 */
@Configuration
public class FeignConfig {

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL; // 开启全量日志输出
    }
}