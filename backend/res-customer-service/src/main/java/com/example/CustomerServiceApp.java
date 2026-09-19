package com.example;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.example.api")
@MapperScan("com.example.dao.mapper")
public class CustomerServiceApp {
    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApp.class, args);
    }
}