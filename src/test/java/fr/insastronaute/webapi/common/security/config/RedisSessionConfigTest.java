package fr.insastronaute.webapi.common.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.session.MapSession;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;

import java.time.Duration;
import java.util.Map;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RedisSessionConfigTest {

    @Test
    @SuppressWarnings("unchecked")
    void incompleteRedisSessionHashesAreTreatedAsMissingSessions() {
        RedisSessionConfig config = new RedisSessionConfig();
        RedisIndexedSessionRepository repository = mock(RedisIndexedSessionRepository.class);
        SessionRepositoryCustomizer<RedisIndexedSessionRepository> customizer =
                config.redisSessionRepositoryCustomizer(Duration.ofHours(8), "insastronaute:sessions");
        var mapperCaptor = forClass(BiFunction.class);

        customizer.customize(repository);

        verify(repository).setRedisSessionMapper(mapperCaptor.capture());
        BiFunction<String, Map<String, Object>, MapSession> mapper = mapperCaptor.getValue();

        assertThat(mapper.apply("broken", Map.of("lastAccessedTime", 1L))).isNull();
        assertThat(mapper.apply("valid", Map.of(
                "creationTime", 1L,
                "lastAccessedTime", 1L,
                "maxInactiveInterval", 60
        ))).isNotNull();
    }
}
