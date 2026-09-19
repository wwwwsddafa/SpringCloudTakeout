package com.example.configs;



import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * @program: cloud161
 * @description: gateway的路由配置属性类
 * @author: zy
 * @create: 2026-08-19 11:45
 */

/**
 * Gateway动态路由配置
 *
 * 对应Nacos：
 *
 * gateway:
 *   routes:
 *     - id: user-service
 *       uri: lb://user-service
 *       predicates:
 *         - Path=/user/**
 */
@ConfigurationProperties(prefix = "gateway")
public class GatewayRouteProperties {
    /**
     * Gateway路由列表
     */
    private List<RouteDefinition> routes = new ArrayList<>();

    public List<RouteDefinition> getRoutes() {
        return routes;
    }

    public void setRoutes(List<RouteDefinition> routes) {
        this.routes = routes;
    }
}