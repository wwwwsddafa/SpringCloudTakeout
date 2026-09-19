package com.example.configs;



/**
 * @program: cloud161
 * @description: 路由刷新的业务类
 * @author: zy
 * @create: 2026-08-19 11:48
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.java.Log;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Gateway动态路由管理器
 *
 * 负责：
 *
 * 1. 删除旧路由
 * 2. 加载新路由
 * 3. 写入Gateway
 * 4. 刷新Gateway路由缓存
 */
@Log
@Service
public class GatewayRouteService {

    /**
     * Gateway动态路由写入器
     */
    private final RouteDefinitionWriter routeDefinitionWriter;

    /**
     * Gateway路由配置: 读到的nacos配置中心的配置信息是一个字符串，将这个字符串的内容 转换为 GatewayRouteProperties 对象
     */
    private final GatewayRouteProperties routeProperties;

    // 用于将字符串转换为 GatewayRouteProperties 对象
    private final ObjectMapper objectMapper;

    /**
     * Spring事件发布器
     *
     * 用于发布RefreshRoutesEvent
     */
    private final ApplicationEventPublisher applicationEventPublisher;

    public GatewayRouteService(RouteDefinitionWriter routeDefinitionWriter, GatewayRouteProperties routeProperties, ObjectMapper objectMapper, ApplicationEventPublisher applicationEventPublisher) {
        this.routeDefinitionWriter = routeDefinitionWriter;
        this.routeProperties = routeProperties;
        this.objectMapper = objectMapper;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 利用最新的配置信息, 刷新Gateway路由
     */
    public synchronized void refreshRoutes(   String configInfo    ) {
        log.info("========================================");
        log.info("开始刷新 Gateway 动态路由");
        log.info("========================================");
        try {
            //List<RouteDefinition> routes = routeProperties.getRoutes();
            List<RouteDefinition> newRoutes = parseRoutes(configInfo);   // 对配置信息进行解析, 提取路由列表，转换成 RouteDefinition 列表
            if (newRoutes == null) {
                newRoutes = new ArrayList<>();
            }
            log.info("Nacos读取到 "+ newRoutes.size()+" 条路由");
            // 1. 校验路由: 桀查路由是否符合要求, 例如路由ID是否唯一, 是否有必要的属性等
            validateRoutes(newRoutes);
            // 2. 删除旧路由
            clearRoutes(newRoutes);
            // 3. 写入新路由
            saveRoutes(newRoutes);
            // 4. *****刷新Gateway路由缓存
            applicationEventPublisher.publishEvent(new RefreshRoutesEvent(this));
            log.info("========================================");
            log.info("Gateway 动态路由刷新成功");
            log.info("当前路由数量: "+ newRoutes.size());
            log.info("========================================");
        } catch (Exception e) {
            log.info("Gateway 动态路由刷新失败"+e);
        }
    }

    /**
     * 解析Nacos YAML配置
     */
    @SuppressWarnings("unchecked")
    private List<RouteDefinition> parseRoutes(String configInfo) {
        Yaml yaml = new Yaml();
        Map<String, Object> root = yaml.load(configInfo);
        if (root == null) {
            return List.of();
        }
        Object gatewayObject = root.get("gateway");
        if (!(gatewayObject instanceof Map)) {
            throw new IllegalArgumentException("Nacos配置中不存在gateway节点");
        }
        Map<String, Object> gateway = (Map<String, Object>) gatewayObject;
        Object routesObject = gateway.get("routes");
        if (routesObject == null) {
            return List.of();
        }
        if (!(routesObject instanceof List)) {
            throw new IllegalArgumentException("gateway.routes必须是数组");
        }
        List<Object> routeList = (List<Object>) routesObject;
        List<RouteDefinition> result = new ArrayList<>();
        for (Object routeObject : routeList) {
            if (!(routeObject instanceof Map)) {
                throw new IllegalArgumentException("Gateway路由配置格式错误");
            }
            Map<String, Object> routeMap = (Map<String, Object>) routeObject;
            RouteDefinition definition = objectMapper.convertValue(routeMap, RouteDefinition.class);
            result.add(definition);
        }
        return result;
    }

    /**
     * 校验路由
     */
    private void validateRoutes(List<RouteDefinition> routes) {
        //set可以去重
        Set<String> routeIds = new HashSet<>();
        for (RouteDefinition route : routes) {
            if (route.getId() == null || route.getId().isBlank()) {
                throw new IllegalArgumentException("Gateway路由ID不能为空");
            }
            if (!routeIds.add(route.getId())) {
                throw new IllegalArgumentException("Gateway路由ID重复: " + route.getId());
            }
            if (route.getUri() == null) {
                throw new IllegalArgumentException("Gateway路由URI不能为空: " + route.getId());
            }
            log.info("校验路由: id="+route.getId()+", uri="+route.getUri());
        }
    }

    /**
     * 删除旧路由
     *
     * 注意：
     * 这里不是只删除Nacos中存在的路由，
     * 而是删除Gateway当前管理的动态路由。
     */
    private void clearRoutes(List<RouteDefinition> newRoutes) {
        for (RouteDefinition route : newRoutes) {
            String routeId = route.getId();
            try {
                routeDefinitionWriter.delete(Mono.just(routeId)).onErrorResume(e -> {
                    log.info("删除旧路由失败，可能路由不存在: " + routeId);
                    return Mono.empty();
                }).block();
                log.info("删除旧路由成功: "+ routeId);
            } catch (Exception e) {
                log.info("删除旧路由异常: "+routeId);
            }
        }
    }

    /**
     * 保存新路由
     */
    private void saveRoutes(List<RouteDefinition> routes) {
        for (RouteDefinition route : routes) {
            routeDefinitionWriter.save(Mono.just(route)).block();
            log.info("Gateway路由加载成功: id="+route.getId()+", uri="+ route.getUri()
            );
        }
    }
}