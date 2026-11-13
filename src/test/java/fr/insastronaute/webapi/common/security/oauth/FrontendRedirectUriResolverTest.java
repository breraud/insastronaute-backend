package fr.insastronaute.webapi.common.security.oauth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FrontendRedirectUriResolverTest {

    private final FrontendRedirectUriResolver resolver = new FrontendRedirectUriResolver("http://localhost:5173");

    @Test
    void resolvesRelativePathAgainstFrontendBaseUrl() {
        assertThat(resolver.resolveOrDefault("/challenges"))
                .isEqualTo("http://localhost:5173/challenges");
    }

    @Test
    void resolvesAbsoluteUrlWithSameOrigin() {
        assertThat(resolver.resolveOrDefault("http://localhost:5173/profile"))
                .isEqualTo("http://localhost:5173/profile");
    }

    @Test
    void resolvesAbsoluteUrlWithConfiguredAllowedOrigin() {
        FrontendRedirectUriResolver productionResolver = new FrontendRedirectUriResolver(
                "https://example.com",
                List.of("https://example.com", "https://admin.example.com")
        );

        assertThat(productionResolver.resolveOrDefault("https://admin.example.com/challenges"))
                .isEqualTo("https://admin.example.com/challenges");
    }

    @Test
    void fallsBackToFrontendHomeForExternalUrl() {
        assertThat(resolver.resolveOrDefault("https://example.com/phishing"))
                .isEqualTo("http://localhost:5173/");
    }

    @Test
    void fallsBackToFrontendHomeForProtocolRelativeUrl() {
        assertThat(resolver.resolveOrDefault("//example.com/phishing"))
                .isEqualTo("http://localhost:5173/");
    }
}
