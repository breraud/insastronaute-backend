package fr.insastronaute.webapi.common.ratelimit;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import fr.insastronaute.webapi.common.web.RateLimitExceededException;
import fr.insastronaute.webapi.common.web.RateLimitUnavailableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisRateLimiterTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final RedisRateLimiter rateLimiter = new RedisRateLimiter(redisTemplate);
    private final RateLimitPolicy policy = new RateLimitPolicy(
            "checkpoint-validation",
            10,
            Duration.ofMinutes(1)
    );

    @Test
    void allowsRequestWhenLuaScriptReturnsNoRetryDelay() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(),
                any()
        )).thenReturn(-1L);

        assertThatCode(() -> rateLimiter.requireAllowed("player-sub", policy))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsRequestWithRemainingWindowReturnedByRedis() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(),
                any()
        )).thenReturn(4_250L);

        assertThatThrownBy(() -> rateLimiter.requireAllowed("player-sub", policy))
                .isInstanceOf(RateLimitExceededException.class)
                .extracting("retryAfterMs")
                .isEqualTo(4_250L);
    }

    @Test
    void hashesSubjectBeforeUsingItInRedisKey() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(),
                any()
        )).thenReturn(-1L);

        rateLimiter.requireAllowed("private-player-subject", policy);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), any(), any());
        assertThat(keys.getValue()).singleElement().asString()
                .startsWith("insastronaute:rate-limit:checkpoint-validation:")
                .doesNotContain("private-player-subject");
    }

    @Test
    void failsClosedWhenRedisReturnsNoDecision() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(),
                any()
        )).thenReturn(null);

        assertThatThrownBy(() -> rateLimiter.requireAllowed("player-sub", policy))
                .isInstanceOf(RateLimitUnavailableException.class);
    }

    @Test
    void failsClosedWhenRedisConnectionIsUnavailable() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(),
                any()
        )).thenThrow(new RedisConnectionFailureException("Redis unavailable"));

        assertThatThrownBy(() -> rateLimiter.requireAllowed("player-sub", policy))
                .isInstanceOf(RateLimitUnavailableException.class)
                .hasCauseInstanceOf(RedisConnectionFailureException.class);
    }
}
