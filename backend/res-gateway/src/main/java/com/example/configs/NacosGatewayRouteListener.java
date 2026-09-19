package com.example.configs;



/**
 * @program: cloud161
 * @description:
 * @author: zy
 * @create: 2026-08-19 11:55
 */

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * Nacos Gateway路由监听器, 它实现了ApplicationRunner接口, 用于在应用启动时初始化Nacos配置监听
 */
@Slf4j
@Component
public class NacosGatewayRouteListener implements ApplicationRunner {
    private static final String DATA_ID = "gateway-routes.yml";
    private static final String GROUP = "DEFAULT_GROUP";

    @Value("${spring.cloud.nacos.config.server-addr}")//从spring boot配置中获取nacos配置中心的地址
    private String serverAddr;
    @Value("${spring.cloud.nacos.config.namespace:public}")//从spring boot配置中获取nacos配置中心的命名空间
    private String namespace;

    private final GatewayRouteService gatewayRouteService;  //注入路由处理的业务类

    private ConfigService configService;  //注入Nacos配置服务,利用它可以获取Nacos配置变化

    public NacosGatewayRouteListener(GatewayRouteService gatewayRouteService) {
        this.gatewayRouteService = gatewayRouteService;
    }

    //spring boot启动时，会自动回调此方法
    @Override
    public void run(ApplicationArguments args) throws Exception {
        initNacos();   //自已实现读取nacos配置中心的路由配置, 并添加事件监听器
        /*
         * 启动时主动读取一次配置文件 .
         */
        loadInitialRoutes();
    }

    /**
     * 初始化Nacos
     */
    private void initNacos() throws NacosException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, serverAddr);
        properties.put(PropertyKeyConst.NAMESPACE, namespace);

        log.info("启动时，读取到的配置为："+properties );
        configService = NacosFactory.createConfigService(properties);  // 创建Nacos配置服务对象
        configService.addListener(DATA_ID, GROUP, new Listener() {
                    @Override
                    public Executor getExecutor() {
                        return null;
                    }
                    //接收nacos配置变化事件，得到最新的配置内容
                    @Override
                    public void receiveConfigInfo(String configInfo) {
                        log.info("检测到Nacos Gateway路由配置发生变化， 最新的配置为:"+ configInfo);
                        gatewayRouteService.refreshRoutes(configInfo);
                    }
                }
        );
        log.info("Nacos Gateway路由监听器启动成功");
    }

    /**
     * 启动时读取Nacos配置
     */
    private void loadInitialRoutes() throws NacosException {
        String configInfo = configService.getConfig(DATA_ID, GROUP, 5000);
        if (configInfo == null || configInfo.isBlank()) {
            log.info("Nacos中没有找到Gateway路由配置: "+ DATA_ID);
            return;
        }
        log.info("启动时，读取到的Nacos Gateway路由配置为： "+configInfo);
        gatewayRouteService.refreshRoutes(configInfo);
    }
}
