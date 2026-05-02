package com.gera.elevator.store;

import com.gera.elevator.config.ElevatorProperties;
import com.gera.elevator.exception.StateLockTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "elevator.system", name = "state-store", havingValue = "redis", matchIfMissing = true)
public class RedisStateLockService implements StateLockService {

    private static final RedisScript<Long> RELEASE_SCRIPT = RedisScript.of("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ElevatorProperties properties;

    public RedisStateLockService(StringRedisTemplate redisTemplate, ElevatorProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public <T> T withLock(String operationName, Supplier<T> work) {
        String token = UUID.randomUUID().toString();
        long deadline = System.currentTimeMillis() + properties.getLockTimeoutMillis();
        while (System.currentTimeMillis() <= deadline) {
            try {
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                        properties.getRedis().getLockKey(),
                        token,
                        Duration.ofMillis(properties.getRedis().getLockTtlMillis())
                );
                if (Boolean.TRUE.equals(acquired)) {
                    try {
                        return work.get();
                    } finally {
                        release(token);
                    }
                }
                Thread.sleep(25);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new StateLockTimeoutException("Interrupted while waiting for Redis elevator state lock");
            } catch (DataAccessException ex) {
                throw new StateLockTimeoutException("Redis lock is unavailable for operation " + operationName);
            }
        }
        throw new StateLockTimeoutException("Timed out waiting for Redis elevator state lock for operation " + operationName);
    }

    private void release(String token) {
        redisTemplate.execute(RELEASE_SCRIPT, List.of(properties.getRedis().getLockKey()), token);
    }
}
