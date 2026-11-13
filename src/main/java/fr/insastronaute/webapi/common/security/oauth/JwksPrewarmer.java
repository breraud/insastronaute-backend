package fr.insastronaute.webapi.common.security.oauth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class JwksPrewarmer {
    private static final Logger LOGGER = LoggerFactory.getLogger(JwksPrewarmer.class);
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${spring.security.oauth2.client.provider.keycloak.jwk-set-uri:}")
    private String jwkSetUri;

    @EventListener(ApplicationReadyEvent.class)
    public void prewarmJwks() {
        if (jwkSetUri != null && !jwkSetUri.isBlank()) {
            try {
                LOGGER.info("Pre-warming Keycloak JWKS cache at {}", jwkSetUri);
                String response = restTemplate.getForObject(jwkSetUri, String.class);
                LOGGER.info("Successfully pre-warmed JWKS. Payload length: {}", response != null ? response.length() : 0);
            } catch (Exception e) {
                LOGGER.warn("Failed to pre-warm JWKS cache: {}", e.getMessage());
            }
        }
    }
}
