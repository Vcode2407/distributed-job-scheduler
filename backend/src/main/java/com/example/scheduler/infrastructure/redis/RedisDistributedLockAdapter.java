package com.example.scheduler.infrastructure.redis;

import com.example.scheduler.application.port.out.DistributedLockPort;
import java.time.Duration;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisDistributedLockAdapter implements DistributedLockPort {

    private final StringRedisTemplate redisTemplate;

    public RedisDistributedLockAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean acquire(String key, String owner, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey(key), owner, ttl);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void release(String key, String owner) {
        String namespacedKey = lockKey(key);
        String currentOwner = redisTemplate.opsForValue().get(namespacedKey);
        if (Objects.equals(owner, currentOwner)) {
            redisTemplate.delete(namespacedKey);
        }
    }

    private String lockKey(String key) {
        return "scheduler:lock:" + key;
    }
}
