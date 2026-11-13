package fr.insastronaute.webapi.common.ratelimit;

import fr.insastronaute.webapi.common.web.RateLimitExceededException;
import fr.insastronaute.webapi.common.web.RateLimitUnavailableException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public class RedisRateLimiter {

    private static final String KEY_PREFIX = "insastronaute:rate-limit:";
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            if count > tonumber(ARGV[2]) then
              local retry = redis.call('PTTL', KEYS[1])
              if retry < 1 then
                return 1
              end
              return retry
            end
            return -1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void requireAllowed(String subject, RateLimitPolicy policy) {
        try {
            Long retryAfterMs = redisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    List.of(redisKey(subject, policy)),
                    Long.toString(policy.window().toMillis()),
                    Integer.toString(policy.limit())
            );
            if (retryAfterMs == null) {
                throw new RateLimitUnavailableException();
            }
            if (retryAfterMs >= 0) {
                throw new RateLimitExceededException(retryAfterMs);
            }
        } catch (DataAccessException exception) {
            throw new RateLimitUnavailableException(exception);
        }
    }

    private static String redisKey(String subject, RateLimitPolicy policy) {
        return KEY_PREFIX + policy.id() + ":" + sha256(subject);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponible.", exception);
        }
    }
}
