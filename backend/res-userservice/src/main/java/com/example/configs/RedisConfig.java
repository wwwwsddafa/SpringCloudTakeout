package com.example.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);// ① 设置Redis连接

        StringRedisSerializer stringSerializer = new StringRedisSerializer();// ② Key用字符串序列化
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();// ③ Value用JSON序列化

        template.setKeySerializer(stringSerializer);// ④ Key的序列化方式 → 字符串
        template.setHashKeySerializer(stringSerializer);// ⑤ HashKey的序列化方式 → 字符串
        template.setValueSerializer(jsonSerializer);// ⑥ Value的序列化方式 → JSON
        template.setHashValueSerializer(jsonSerializer);// ⑦ HashValue的序列化方式 → JSON

        template.afterPropertiesSet();// ⑧ 初始化模板
        return template;
    }
}