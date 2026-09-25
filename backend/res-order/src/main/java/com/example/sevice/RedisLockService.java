package com.example.sevice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RedisLockService {

    private static final String LOCK_PREFIX = "lock:order:";

    private static final long LOCK_TIMEOUT_SECONDS = 10;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 每个线程持有的锁标识（UUID），用于释放时校验身份。
     * 使用 ThreadLocal 保证同一线程内的锁标识一致性。
     */
    private final ThreadLocal<String> lockHolder = new ThreadLocal<>();

    /**
     * Lua 脚本：原子性地校验锁的持有者并释放。
     * 只有当 key 对应的 value 与当前线程持有的 value 一致时才删除，
     * 避免误删其他线程刚获取到的锁。
     */
    private static final String UNLOCK_LUA_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

    private volatile DefaultRedisScript<Long> unlockScript;

    private DefaultRedisScript<Long> getUnlockScript() {
        if (unlockScript == null) {
            synchronized (this) {
                if (unlockScript == null) {
                    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                    script.setScriptText(UNLOCK_LUA_SCRIPT);
                    script.setResultType(Long.class);
                    unlockScript = script;
                }
            }
        }
        return unlockScript;
    }

    /**
     * 尝试获取分布式锁
     *
     * @param userId 用户ID，作为锁的粒度
     * @return true 获取成功，false 获取失败（说明该用户正在下单中）
     */
    public boolean tryLock(String userId) {
        String lockKey = LOCK_PREFIX + userId;
        // 使用 UUID 作为锁的持有者标识，而非时间戳
        // 这样即使多台机器在同一毫秒内竞争，也能保证持有者唯一
        String lockValue = UUID.randomUUID().toString();
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue,
                        LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        boolean locked = Boolean.TRUE.equals(success);
        if (locked) {
            lockHolder.set(lockValue);
            log.info("获取分布式锁成功: userId={}, lockValue={}", userId, lockValue);
        } else {
            log.warn("获取分布式锁失败（重复提交）: userId={}", userId);
        }
        return locked;
    }

    /**
     * 释放分布式锁（原子操作）
     *
     * 通过 Lua 脚本保证「校验持有者 + 删除 key」两步的原子性：
     * 1. 如果锁已过期被 Redis 自动删除 → 不误删别人的锁
     * 2. 如果锁已被其他线程获取 → 不误删别人的锁
     * 3. 只有锁的 value 与当前线程持有的 value 一致时才删除
     */
    public void unlock(String userId) {
        String lockKey = LOCK_PREFIX + userId;
        String lockValue = lockHolder.get();
        if (lockValue == null) {
            log.warn("当前线程未持有分布式锁，跳过释放: userId={}", userId);
            return;
        }
        try {
            Long result = redisTemplate.execute(
                    getUnlockScript(),
                    Collections.singletonList(lockKey),
                    lockValue);
            if (result != null && result == 1L) {
                log.info("释放分布式锁成功: userId={}", userId);
            } else {
                log.warn("释放分布式锁失败（锁已过期或被其他线程持有）: userId={}", userId);
            }
        } finally {
            lockHolder.remove();
        }
    }
}