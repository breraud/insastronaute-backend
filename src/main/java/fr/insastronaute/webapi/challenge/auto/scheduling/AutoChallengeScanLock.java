package fr.insastronaute.webapi.challenge.auto.scheduling;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class AutoChallengeScanLock {

    private static final String LOCK_KEY = "scheduler:auto-challenges";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public AutoChallengeScanLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<String> tryAcquire(Duration duration) {
        String owner = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, owner, duration);
        return Boolean.TRUE.equals(acquired) ? Optional.of(owner) : Optional.empty();
    }

    public void release(String owner) {
        redisTemplate.execute(RELEASE_SCRIPT, List.of(LOCK_KEY), owner);
    }
}
