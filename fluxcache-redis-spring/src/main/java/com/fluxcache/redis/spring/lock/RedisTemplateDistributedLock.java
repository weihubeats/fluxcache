package com.fluxcache.redis.spring.lock;

import com.fluxcache.core.lock.FluxDistributedLock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple Redis distributed lock using SET NX EX + token compare delete.
 */
@RequiredArgsConstructor
public class RedisTemplateDistributedLock implements FluxDistributedLock {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final Map<String, String> heldTokens = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(String key, long waitSeconds, long leaseSeconds) {
        String token = UUID.randomUUID().toString();
        long deadline = System.currentTimeMillis() + Math.max(waitSeconds, 0) * 1000L;
        do {
            Boolean ok = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, token, Duration.ofSeconds(Math.max(leaseSeconds, 1)));
            if (Boolean.TRUE.equals(ok)) {
                heldTokens.put(key, token);
                return true;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        } while (System.currentTimeMillis() <= deadline);
        return false;
    }

    @Override
    public void unlock(String key) {
        String token = heldTokens.remove(key);
        if (token == null) {
            return;
        }
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), token);
    }
}
