package fr.insastronaute.webapi.auth.gate;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Le passage prioritaire.
 *
 * Il ne remplace pas l'authentification : le detenteur du code arrive devant Keycloak comme
 * tout le monde, et Keycloak decide seul. Ce que le code achete, c'est de ne pas attendre
 * derriere deux mille etudiants un matin d'inte.
 */
class AuthGateFastPassTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final AuthGateTokenService tokenService = mock(AuthGateTokenService.class);
    private final AuthGateProperties properties = new AuthGateProperties();

    private AuthGateService service() {
        return new AuthGateService(redisTemplate, tokenService, properties);
    }

    @Test
    void theRightCodeSkipsTheQueue() {
        properties.setFastPassCode("le-code");
        when(tokenService.generateToken(anyString(), any(), any(Instant.class))).thenReturn("jeton");

        Optional<fr.insastronaute.webapi.auth.gate.dto.GateEnterResponse> admitted =
                service().admitWithFastPass("le-code", "https://admin.insastronaute.fr/");

        assertThat(admitted).isPresent();
        assertThat(admitted.get().status()).isEqualTo(GateStatus.ADMITTED);
        assertThat(admitted.get().redirectUrl()).contains("gate_token=jeton");
    }

    @Test
    void aWrongCodeIsRefused() {
        properties.setFastPassCode("le-code");

        assertThat(service().admitWithFastPass("autre-chose", "https://admin.insastronaute.fr/"))
                .isEmpty();
    }

    /**
     * Sans code configure, le raccourci n'existe pas -- et surtout, une chaine vide envoyee
     * par un client ne doit pas se trouver egale au secret vide par defaut.
     */
    @Test
    void noConfiguredCodeMeansNoShortcut() {
        assertThat(service().admitWithFastPass("", "https://admin.insastronaute.fr/")).isEmpty();
        assertThat(service().admitWithFastPass(null, "https://admin.insastronaute.fr/")).isEmpty();
    }

    @Test
    void aMissingCodeIsRefusedEvenWhenOneIsConfigured() {
        properties.setFastPassCode("le-code");

        assertThat(service().admitWithFastPass(null, "https://admin.insastronaute.fr/")).isEmpty();
    }
}
