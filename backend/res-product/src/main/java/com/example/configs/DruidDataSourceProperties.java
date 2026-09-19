package com.example.configs;



import com.alibaba.druid.pool.DruidDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * @program: cloud161
 * @description:
 * @author: zy
 * @create: 2026-08-18 16:38
 */
@Configuration
@Slf4j
//@RefreshScope
//@ConfigurationProperties(prefix = "spring.datasource")
public class DruidDataSourceProperties {


    @Value("${spring.datasource.url}")
    private String url;
    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;
    @Value("${spring.datasource.username}")
    private String username;
    @Value("${spring.datasource.password}")
    private String password;

    @Bean  //IOC
    @Primary  //优先使用这个代码IOC
    @RefreshScope
    public DataSource druid(){
        log.info("使用的编程式的数据源创建.");
        DruidDataSource ds=new DruidDataSource();
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName(this.driverClassName);
        ds.setUrl(url);

        // 重要：Druid 必须通过 init() 方法初始化
        try {
            ds.init();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        return  ds;
    }

}


/*
*
* @Component + @RefreshScope	✅	普通 Bean，只有一层代理
*@Bean 方法 + @RefreshScope	✅	只代理返回的 Bean，不影响配置类
* @Configuration 类 + @RefreshScope	❌	两层代理冲突，@Value 注入时机错乱
*
* 为什么 @RefreshScope 不能加在 @Configuration 类上？
1. @Configuration 类是特殊的 Bean
@Configuration 类本身会被 Spring 创建为 CGLIB 代理，目的是保证 @Bean 方法之间的调用返回的是同一个 Bean（即"Full Mode"）。Spring 已经为它创建了一层代理。
2. @RefreshScope 又要创建一层代理
@RefreshScope 的工作原理是：把原始 Bean 包装成一个 Scoped Proxy（名为 scopedTarget.xxx），真正的实例延迟创建，每次访问时检查是否需要刷新
两层代理冲突了。
*
* */

/*
* 这里有一个隐患：@Value 字段在配置类创建时注入，Nacos 配置变更后，配置类本身的 @Value 字段不会自动更新。所以即使 druid() 方法被重新调用，读到的还是旧值。
* 更可靠的写法
如果你希望数据源配置真正能动态刷新，应该直接在 druid() 方法里从 Environment 读取，而不是依赖类的 @Value 字段
*
* */