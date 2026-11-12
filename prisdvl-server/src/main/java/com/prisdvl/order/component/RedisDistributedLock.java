package com.prisdvl.order.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 基于 Redis 的轻量分布式锁，用于定时任务多实例互斥。
 *
 * <p>加锁使用 {@code SET key value NX PX ttl}；解锁用 Lua 比对 value 后删除，
 * 避免误删其他实例续期后的锁。</p>
 */
@Component
@Slf4j
public class RedisDistributedLock {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    public RedisDistributedLock(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 尝试获取锁。
     *
     * @param key 锁 key
     * @param ttl 锁过期时间，防止实例宕机后死锁
     * @return 生成的持有者标识；获取失败返回 null
     */
    public String tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        if (Boolean.TRUE.equals(success)) {
            return token;
        }
        return null;
    }

    /**
     * 释放锁，只有持有者标识一致时才删除。
     *
     * @param key   锁 key
     * @param token 加锁时返回的持有者标识
     */
    public void unlock(String key, String token) {
        if (token == null) {
            return;
        }
        try {
            stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), token);
        } catch (Exception e) {
            // 解锁失败不影响业务，锁会按 TTL 自动过期
            log.warn("释放分布式锁失败，key={}", key, e);
        }
    }
}
