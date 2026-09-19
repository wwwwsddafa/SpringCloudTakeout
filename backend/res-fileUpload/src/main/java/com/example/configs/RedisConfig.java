package com.example.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean // 将方法返回对象交给IOC容器管理
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Jackson JSON序列化器：把对象转JSON
        GenericJackson2JsonRedisSerializer jackson = new GenericJackson2JsonRedisSerializer();
        // String序列化器：只处理字符串
        StringRedisSerializer str = new StringRedisSerializer();

        // ========== 重点序列化配置 ==========
        template.setKeySerializer(str);         //普通key：字符串序列化
        template.setValueSerializer(jackson);   //普通value：对象转JSON存储

        template.setHashKeySerializer(str);     //Hash结构的field（hashKey）用字符串
        template.setHashValueSerializer(jackson);//Hash结构的value用JSON序列化

        template.afterPropertiesSet(); // 初始化加载配置，必须调用
        return template;
    }
}