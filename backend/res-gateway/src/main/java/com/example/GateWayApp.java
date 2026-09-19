package com.example;



import com.example.configs.GatewayRouteProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * @program: cloud161
 * @description:
 * @author: zy
 * @create: 2026-08-19 09:00
 **/
@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(GatewayRouteProperties.class)  //开启自动配置
public class GateWayApp {
    public static void main(String[] args) {
        SpringApplication.run(GateWayApp.class, args);
    }
}