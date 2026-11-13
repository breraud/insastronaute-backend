package fr.insastronaute.webapi.auth.gate;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AuthGateTokenServiceTest {

    private final AuthGateProperties properties = new AuthGateProperties();
    private final AuthGateTokenService service = new AuthGateTokenService(properties);

    @Test
    void generatesAndValidatesValidGateToken() {
        properties.setSecret("test-secret-key-that-is-long-enough-for-hmac-sha256-security-requirements-12345");
        Instant expiresAt = Instant.now().plus(Duration.ofSeconds(60));

        String token = service.generateToken("ticket-123", "/evenements", expiresAt);
        assertThat(token).isNotBlank();

        Optional<AuthGateTokenService.ValidatedGateToken> validated = service.validateToken(token);
        assertThat(validated).isPresent();
        assertThat(validated.get().ticketId()).isEqualTo("ticket-123");
        assertThat(validated.get().redirectUri()).isEqualTo("/evenements");
    }

    @Test
    void rejectsExpiredGateToken() {
        properties.setSecret("test-secret-key-that-is-long-enough-for-hmac-sha256-security-requirements-12345");
        Instant expiresAt = Instant.now().minus(Duration.ofSeconds(5));

        String token = service.generateToken("ticket-123", "/evenements", expiresAt);
        Optional<AuthGateTokenService.ValidatedGateToken> validated = service.validateToken(token);
        assertThat(validated).isEmpty();
    }

    @Test
    void rejectsTamperedGateToken() {
        properties.setSecret("test-secret-key-that-is-long-enough-for-hmac-sha256-security-requirements-12345");
        Instant expiresAt = Instant.now().plus(Duration.ofSeconds(60));

        String token = service.generateToken("ticket-123", "/evenements", expiresAt);
        String tampered = token.substring(0, token.length() - 4) + "XXXX";
        Optional<AuthGateTokenService.ValidatedGateToken> validated = service.validateToken(tampered);
        assertThat(validated).isEmpty();
    }
}
