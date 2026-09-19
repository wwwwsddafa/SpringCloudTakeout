package com.example;



import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * @program: cloud161
 * @description:
 * @author: zy
 * @create: 2026-08-16 15:28
 */
@SpringBootApplication
@EnableDiscoveryClient
public class FileUploadApp {
    public static void main(String[] args) {
        SpringApplication.run(FileUploadApp.class, args);
    }
}


/*
* @EnableDiscoveryClient 是 Spring Cloud 的注解，用于启用服务发现功能。

核心功能：
1. 服务注册
应用启动时，自动将自己注册到服务注册中心（如 Nacos、Eureka、Consul）
注册信息包括：服务名、IP、端口、健康状态等
2. 服务发现
可以从注册中心获取其他服务的实例列表
配合 DiscoveryClient 使用，例如：
List<ServiceInstance> instances = discoveryClient.getInstances("serviceName");
*
*
* */