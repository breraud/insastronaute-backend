package fr.insastronaute.webapi.auth.gate;

import fr.insastronaute.webapi.auth.gate.dto.GateEnterResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthGateServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final AuthGateTokenService tokenService = mock(AuthGateTokenService.class);
    private final AuthGateProperties properties = new AuthGateProperties();
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);

    private AuthGateService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        service = new AuthGateService(redisTemplate, tokenService, properties);
    }

    /**
     * Un ticket n'entrait dans la file que par une admission : qui fermait son onglet
     * en salle d'attente y laissait le sien pour toujours. Le compte affiché mesurait
     * l'histoire du serveur, pas ses visiteurs.
     */
    @Test
    void enteringFirstDropsTheTicketsNobodyWillClaim() {
        when(zSetOperations.size(anyString())).thenReturn(0L);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(tokenService.generateToken(anyString(), anyString(), any(Instant.class))).thenReturn("valid-token");

        service.enter(null, "/evenements");

        org.mockito.ArgumentCaptor<Double> cutoff = org.mockito.ArgumentCaptor.forClass(Double.class);
        verify(zSetOperations).removeRangeByScore(eq("auth:gate:queue"), eq(Double.NEGATIVE_INFINITY), cutoff.capture());

        long expected = Instant.now().minus(properties.getTicketTtl()).toEpochMilli();
        assertThat(cutoff.getValue().longValue())
                .as("la coupure suit ticketTtl, déclaré depuis le début et jamais lu jusqu'ici")
                .isBetween(expected - 5_000L, expected + 5_000L);
    }

    /**
     * Le chemin rapide est contourné dès que la file n'est pas vide. Sans purge, un
     * seul ticket abandonné mettait tous les suivants en attente, dont une part
     * abandonnait à son tour : la file ne pouvait plus jamais redescendre à zéro.
     */
    @Test
    void aQueueMadeOnlyOfExpiredTicketsLetsTheNextVisitorStraightIn() {
        // La purge vide la file, et c'est ce que size() rend ensuite.
        when(zSetOperations.size(anyString())).thenReturn(0L);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(tokenService.generateToken(anyString(), anyString(), any(Instant.class))).thenReturn("valid-token");

        GateEnterResponse response = service.enter(null, "/evenements");

        assertThat(response.status()).isEqualTo(GateStatus.ADMITTED);
        verify(zSetOperations, never()).add(anyString(), anyString(), anyDouble());
    }

    @Test
    void admitsDirectlyWhenCapacityIsAvailable() {
        when(zSetOperations.size(anyString())).thenReturn(0L);
        when(valueOperations.increment(anyString())).thenReturn(5L);
        when(tokenService.generateToken(anyString(), anyString(), any(Instant.class))).thenReturn("valid-token");

        GateEnterResponse response = service.enter(null, "/evenements");

        assertThat(response.status()).isEqualTo(GateStatus.ADMITTED);
        assertThat(response.gateToken()).isEqualTo("valid-token");
        assertThat(response.redirectUrl()).contains("gate_token=valid-token");
    }

    @Test
    void enqueuesWhenRateLimitExceeded() {
        when(zSetOperations.size(anyString())).thenReturn(10L);
        when(zSetOperations.rank(anyString(), anyString())).thenReturn(5L);

        GateEnterResponse response = service.enter(null, "/evenements");

        assertThat(response.status()).isEqualTo(GateStatus.QUEUED);
        assertThat(response.ticketId()).isNotBlank();
        assertThat(response.position()).isEqualTo(6);
    }
}
