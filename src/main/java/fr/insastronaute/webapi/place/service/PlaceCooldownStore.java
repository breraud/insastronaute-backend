package fr.insastronaute.webapi.place.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.time.Instant;

@Component
public class PlaceCooldownStore {

    private final StringRedisTemplate redisTemplate;

    public PlaceCooldownStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public CooldownAttempt tryAcquire(CooldownType type, UUID userId, long durationMs) {
        String key = "user:cooldown:" + type.key + ":" + userId;
        String owner = UUID.randomUUID().toString();
        Instant deadline = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow().plusMillis(durationMs);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, owner, Duration.ofMillis(durationMs));

        if (Boolean.TRUE.equals(acquired)) {
            return new CooldownAttempt(true, 0, deadline, owner);
        }

        Long remaining = redisTemplate.getExpire(key, java.util.concurrent.TimeUnit.MILLISECONDS);
        return new CooldownAttempt(false, remaining == null || remaining < 0 ? 0 : remaining, null, null);
    }

    /**
     * Ce qu'il reste à attendre, sans prendre le verrou.
     *
     * Un pixel acheté ne consomme pas le cooldown — c'est ce qu'on achète — mais
     * le front continue d'afficher le décompte en cours, qui doit donc rester
     * lisible sans l'avancer.
     */
    public long remainingMs(CooldownType type, UUID userId) {
        Long remaining = redisTemplate.getExpire("user:cooldown:" + type.key + ":" + userId,
                java.util.concurrent.TimeUnit.MILLISECONDS);
        return remaining == null || remaining < 0 ? 0 : remaining;
    }

    public void release(CooldownType type, UUID userId, String owner) {
        if (owner == null) return;
        String key = "user:cooldown:" + type.key + ":" + userId;
        redisTemplate.execute(new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end return 0",
                Long.class), java.util.List.of(key), owner);
    }

    public enum CooldownType {
        BASE("base"),
        CHALLENGE("challenge");

        private final String key;

        CooldownType(String key) {
            this.key = key;
        }
    }

    public record CooldownAttempt(boolean acquired, long remainingCooldownMs, Instant deadline, String owner) {
        public CooldownAttempt(boolean acquired, long remainingCooldownMs) {
            this(acquired, remainingCooldownMs, null, null);
        }
    }
}
