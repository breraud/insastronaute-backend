package fr.insastronaute.webapi.challenge.auto.scheduling;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoChallengeScanLockTest {

    @Test
    void acquiresLockAtomicallyWithUniqueOwnerAndExpiry() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        Duration duration = Duration.ofMinutes(12);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq("scheduler:auto-challenges"), any(String.class), eq(duration)))
                .thenReturn(true);

        Optional<String> owner = new AutoChallengeScanLock(redisTemplate).tryAcquire(duration);

        assertThat(owner).isPresent();
        verify(values).setIfAbsent("scheduler:auto-challenges", owner.orElseThrow(), duration);
    }

    @Test
    void returnsEmptyWhenAnotherOwnerHoldsLock() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(false);

        assertThat(new AutoChallengeScanLock(redisTemplate).tryAcquire(Duration.ofMinutes(1))).isEmpty();
    }

    @Test
    void releasesOnlyWhenOwnerTokenStillMatches() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        AutoChallengeScanLock lock = new AutoChallengeScanLock(redisTemplate);
        ArgumentCaptor<RedisScript<Long>> script = ArgumentCaptor.forClass(RedisScript.class);

        lock.release("owner-token");

        verify(redisTemplate).execute(script.capture(), eq(List.of("scheduler:auto-challenges")), eq("owner-token"));
        assertThat(script.getValue().getScriptAsString())
                .contains("redis.call('GET', KEYS[1]) == ARGV[1]")
                .contains("redis.call('DEL', KEYS[1])");
    }
}
