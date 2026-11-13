package fr.insastronaute.webapi.common.security.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class ProductionSecurityValidator implements ApplicationRunner {

    private final AppSecurityProperties securityProperties;

    public ProductionSecurityValidator(AppSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!securityProperties.isProductionHardeningEnabled()) {
            return;
        }

        URI frontendUri;
        try {
            frontendUri = URI.create(securityProperties.getFrontendBaseUrl());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Production hardening requires a valid frontend URL.", exception);
        }
        if (!"https".equalsIgnoreCase(frontendUri.getScheme())) {
            throw new IllegalStateException("Production hardening requires an HTTPS frontend URL.");
        }
        if (securityProperties.getCookieDomain().isEmpty()) {
            throw new IllegalStateException("Production hardening requires a cookie domain.");
        }
    }
}
