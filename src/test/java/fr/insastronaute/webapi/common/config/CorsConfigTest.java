package fr.insastronaute.webapi.common.config;

import fr.insastronaute.webapi.common.security.config.AppSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void allowsConfiguredSubdomainOriginsWithCredentialsForAllPaths() {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setDomain("example.com");

        CorsConfigurationSource source = new CorsConfig(properties).corsConfigurationSource();
        MockHttpServletRequest logoutRequest = new MockHttpServletRequest("POST", "/logout");
        logoutRequest.addHeader("Origin", "https://app.example.com");

        CorsConfiguration configuration = source.getCorsConfiguration(logoutRequest);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins())
                .containsExactly(
                        "https://example.com",
                        "https://admin.example.com",
                        "https://app.example.com"
                );
        assertThat(configuration.getAllowedMethods())
                .containsExactlyElementsOf(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        assertThat(configuration.getAllowedHeaders()).containsExactly("*");
        assertThat(configuration.getExposedHeaders())
                .containsExactly("X-Place-Revision", "Retry-After");
        assertThat(configuration.getAllowCredentials()).isTrue();
        assertThat(configuration.checkOrigin("https://app.example.com")).isEqualTo("https://app.example.com");
    }
}
