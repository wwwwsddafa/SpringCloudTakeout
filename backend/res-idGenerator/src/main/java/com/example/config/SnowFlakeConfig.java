package com.example.config;

import com.example.service.DataCenterIdAssigner;
import com.example.service.SnowFlakeIdGenerator;
import com.example.service.WorkerIdAssigner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class SnowFlakeConfig {

    private final DataCenterIdAssigner dataCenterIdAssigner;

    private final WorkerIdAssigner workerIdAssigner;

    // 构造器注入，去掉字段上的@Autowired
    public SnowFlakeConfig(DataCenterIdAssigner dataCenterIdAssigner, WorkerIdAssigner workerIdAssigner) {
        this.dataCenterIdAssigner = dataCenterIdAssigner;
        this.workerIdAssigner = workerIdAssigner;
    }

    @Bean
    public SnowFlakeIdGenerator snowFlakeIdGenerator(){
        return new SnowFlakeIdGenerator(dataCenterIdAssigner, workerIdAssigner);
    }
}
/*
* 配置类的核心作用：
1. 注册无法加注解的类
第三方库的类（你没法改源码加 @Component）
比如 RestTemplate、DataSource 等
*
2. 复杂Bean的创建逻辑
需要额外初始化步骤
需要条件判断
需要读取配置文件动态创建
*
 3. 集中管理相关Bean
把一组相关的Bean放在一个配置类中
*
* */