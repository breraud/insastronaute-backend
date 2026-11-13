package fr.insastronaute.webapi.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTest {

    @Test
    void objectMapperSupportsJavaTimeValues() throws Exception {
        ObjectMapper objectMapper = new JacksonConfig().objectMapper();

        String json = objectMapper.writeValueAsString(new InstantDto(Instant.parse("2026-07-09T14:29:18Z")));

        assertThat(json).contains("2026-07-09T14:29:18Z");
    }

    private record InstantDto(Instant releasedAt) {
    }
}
