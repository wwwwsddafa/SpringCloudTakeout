package com.example.configs;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClientConfiguration;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/***
 * @program: cloud161
 * @description: 这是一个基于restTemplate客户端的配置类，用于托管一个客户端程序
 * @author: zy
 * @create: 2026-08-15 16:18
 **/
@Configuration
//@LoadBalancerClient(name="idGenerator",configuration= ZyLoadBalancerConfig.class)
@LoadBalancerClients(value = {
        @LoadBalancerClient(name = "idGenerator", configuration = ZyLoadBalancerConfig.class),
        @LoadBalancerClient(name = "fileUpload", configuration = ZyLoadBalancerConfig2.class)
},defaultConfiguration = LoadBalancerClientConfiguration.class
)
public class RestTemplateClientConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate(); // RestTemplate是spring官方提供的一个客户端。
    }
}