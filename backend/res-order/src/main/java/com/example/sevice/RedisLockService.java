package com.example.sevice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RedisLockService {

    private static final String LOCK_PREFIX = "lock:order:";

    private static final long LOCK_TIMEOUT_SECONDS = 10;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 尝试获取分布式锁
     *
     * @param userId 用户ID，作为锁的粒度
     * @return true 获取成功，false 获取失败（说明该用户正在下单中）
     */
    public boolean tryLock(String userId) {
        String lockKey = LOCK_PREFIX + userId;
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, String.valueOf(System.currentTimeMillis()),
                        LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        boolean locked = Boolean.TRUE.equals(success);
        if (locked) {
            log.info("获取分布式锁成功: userId={}", userId);
        } else {
            log.warn("获取分布式锁失败（重复提交）: userId={}", userId);
        }
        return locked;
    }

    /**
     * 释放分布式锁
     */
    public void unlock(String userId) {
        String lockKey = LOCK_PREFIX + userId;
        redisTemplate.delete(lockKey);
        log.info("释放分布式锁: userId={}", userId);
    }
}