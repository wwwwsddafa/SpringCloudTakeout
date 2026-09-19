package com.example;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/***
 * @program: cloud161
 * @description:
 * @author: zy
 * @create: 2026-08-15 15:14
 **/
@SpringBootApplication
@EnableDiscoveryClient
public class IdGeneratorApp {
    public static void main(String[] args) {
        SpringApplication.run(IdGeneratorApp.class, args);
    }
}