package fr.insastronaute.webapi.common.security.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppSecurityPropertiesTest {

    @Test
    void usesLocalFrontendWhenNoDomainOrFrontendBaseUrlIsConfigured() {
        AppSecurityProperties properties = new AppSecurityProperties();

        assertThat(properties.getFrontendBaseUrl()).isEqualTo("http://localhost:5173");
        assertThat(properties.getAllowedOrigins()).containsExactly("http://localhost:5173");
        assertThat(properties.getCookieDomain()).isEmpty();
    }

    @Test
    void derivesRootAndConfiguredSubdomainOriginsFromDomain() {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setDomain("example.com");

        assertThat(properties.getFrontendBaseUrl()).isEqualTo("https://example.com");
        assertThat(properties.getAllowedOrigins())
                .containsExactly(
                        "https://example.com",
                        "https://admin.example.com",
                        "https://app.example.com"
                );
        assertThat(properties.getCookieDomain()).contains("example.com");
    }

    @Test
    void allowsCustomSubdomainsFromConfiguration() {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setDomain("https://example.com/");
        properties.setAllowedSubdomains(List.of("admin", "app"));

        assertThat(properties.getAllowedOrigins())
                .containsExactly(
                        "https://example.com",
                        "https://admin.example.com",
                        "https://app.example.com"
                );
    }

    @Test
    void keepsDefaultSubdomainsWhenConfigurationOverrideIsBlank() {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setDomain("example.com");
        properties.setAllowedSubdomains(List.of(""));

        assertThat(properties.getAllowedOrigins())
                .containsExactly(
                        "https://example.com",
                        "https://admin.example.com",
                        "https://app.example.com"
                );
    }

    @Test
    void keepsExplicitFrontendBaseUrlAsRedirectBaseAndCorsOrigin() {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setFrontendBaseUrl("https://app.example.com/");
        properties.setDomain("example.com");

        assertThat(properties.getFrontendBaseUrl()).isEqualTo("https://app.example.com");
        assertThat(properties.getAllowedOrigins())
                .containsExactly(
                        "https://app.example.com",
                        "https://example.com",
                        "https://admin.example.com"
                );
    }

    @Test
    void derivesCookieDomainFromConfiguredDomainWithoutSchemeOrPort() {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setDomain("https://example.com:8443/");

        assertThat(properties.getCookieDomain()).contains("example.com");
    }

    @Test
    void removesConfiguredLeadingDotFromCookieDomain() {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setDomain(".example.com");

        assertThat(properties.getCookieDomain()).contains("example.com");
    }

    @Test
    void productionHardeningRejectsAnInsecureFrontendUrl() {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setProductionHardeningEnabled(true);
        properties.setFrontendBaseUrl("http://app.example.com");
        properties.setDomain("example.com");

        assertThatThrownBy(() -> new ProductionSecurityValidator(properties).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Production hardening requires an HTTPS frontend URL.");
    }

    @Test
    void productionHardeningRejectsAMissingCookieDomain() {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setProductionHardeningEnabled(true);
        properties.setFrontendBaseUrl("https://app.example.com");

        assertThatThrownBy(() -> new ProductionSecurityValidator(properties).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Production hardening requires a cookie domain.");
    }

    @Test
    void productionHardeningAcceptsHttpsAndAnExplicitCookieDomain() throws Exception {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setProductionHardeningEnabled(true);
        properties.setFrontendBaseUrl("https://app.example.com");
        properties.setDomain("example.com");

        new ProductionSecurityValidator(properties).run(null);
    }
}
