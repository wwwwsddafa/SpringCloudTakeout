package com.example.configs;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FrontendRouteConfig {

    /**
     * 将前端页面请求转发到 Vite 开发服务器 (localhost:5173)
     * 排除 API 路径，避免拦截后端接口请求
     */
    @Bean
    public RouteLocator frontendRoute(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("frontend", r -> r
                        .order(9999)
                        .path("/**")
                        .and()
                        .not(notPredicate -> notPredicate
                                .path("/user/**", "/cart/**", "/order/**",
                                      "/product/**", "/id/**", "/file/**",
                                      "/admin/**", "/actuator/**"))
                        .uri("http://localhost:5173"))
                .build();
    }
}