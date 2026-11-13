package fr.insastronaute.webapi.common.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.SequencedSet;

@Component
@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {

    private static final String DEFAULT_FRONTEND_BASE_URL = "http://localhost:5173";
    private static final List<String> DEFAULT_ALLOWED_SUBDOMAINS = List.of("admin", "app");
    private static final String HTTPS_SCHEME = "https://";

    private String frontendBaseUrl;
    private String domain;
    private boolean openapiDocsPublic;
    private boolean productionHardeningEnabled;
    private List<String> allowedSubdomains = DEFAULT_ALLOWED_SUBDOMAINS;

    /**
     * Combien de temps une session de staff survit a l'inactivite.
     *
     * <p>Plus courte que celle des joueurs, et volontairement. Un compte de staff ouvre des
     * portes qu'un compte de joueur n'ouvre pas ; la duree pendant laquelle un telephone
     * pose sur une table reste ce compte-la doit suivre ce qu'il permet, pas le confort de
     * son porteur.</p>
     */
    private Duration staffSessionTimeout = Duration.ofHours(8);

    /**
     * Combien de temps une session de joueur survit a l'inactivite.
     *
     * <p>Une journee entiere. Reconnecter deux mille etudiants chaque matin, c'est deux
     * mille passages par Keycloak au moment ou tout le monde arrive -- exactement l'afflux
     * que le portail d'attente existe pour lisser.</p>
     */
    private Duration playerSessionTimeout = Duration.ofHours(24);

    public Duration getStaffSessionTimeout() {
        return staffSessionTimeout;
    }

    public void setStaffSessionTimeout(Duration staffSessionTimeout) {
        this.staffSessionTimeout = staffSessionTimeout;
    }

    public Duration getPlayerSessionTimeout() {
        return playerSessionTimeout;
    }

    public void setPlayerSessionTimeout(Duration playerSessionTimeout) {
        this.playerSessionTimeout = playerSessionTimeout;
    }

    public String getFrontendBaseUrl() {
        if (hasText(frontendBaseUrl)) {
            return removeTrailingSlash(frontendBaseUrl.trim());
        }

        if (hasText(domain)) {
            return HTTPS_SCHEME + normalizeDomain(domain);
        }

        return DEFAULT_FRONTEND_BASE_URL;
    }

    public void setFrontendBaseUrl(String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public boolean isOpenapiDocsPublic() { return openapiDocsPublic; }
    public void setOpenapiDocsPublic(boolean openapiDocsPublic) { this.openapiDocsPublic = openapiDocsPublic; }

    public boolean isProductionHardeningEnabled() {
        return productionHardeningEnabled;
    }

    public void setProductionHardeningEnabled(boolean productionHardeningEnabled) {
        this.productionHardeningEnabled = productionHardeningEnabled;
    }

    public List<String> getAllowedSubdomains() {
        return allowedSubdomains;
    }

    public void setAllowedSubdomains(List<String> allowedSubdomains) {
        if (allowedSubdomains == null) {
            return;
        }

        List<String> normalizedSubdomains = allowedSubdomains.stream()
                .filter(AppSecurityProperties::hasText)
                .map(String::trim)
                .toList();

        if (!normalizedSubdomains.isEmpty()) {
            this.allowedSubdomains = normalizedSubdomains;
        }
    }

    public List<String> getAllowedOrigins() {
        SequencedSet<String> origins = new LinkedHashSet<>();

        origins.add(getFrontendBaseUrl());

        if (hasText(domain)) {
            String normalizedDomain = normalizeDomain(domain);

            origins.add(HTTPS_SCHEME + normalizedDomain);
            allowedSubdomains.stream()
                    .filter(AppSecurityProperties::hasText)
                    .map(String::trim)
                    .map(subdomain -> HTTPS_SCHEME + subdomain + "." + normalizedDomain)
                    .forEach(origins::add);
        }

        return List.copyOf(origins);
    }

    public Optional<String> getCookieDomain() {
        if (!hasText(domain)) {
            return Optional.empty();
        }

        return Optional.of(removeLeadingDot(stripPort(normalizeDomain(domain))));
    }

    private static String normalizeDomain(String value) {
        String normalized = removeTrailingSlash(value.trim());

        if (normalized.startsWith("http://")) {
            return normalized.substring("http://".length());
        }

        if (normalized.startsWith(HTTPS_SCHEME)) {
            return normalized.substring(HTTPS_SCHEME.length());
        }

        return normalized;
    }

    private static String removeTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }

        return value;
    }

    private static String stripPort(String value) {
        int portSeparatorIndex = value.lastIndexOf(":");

        if (portSeparatorIndex == -1) {
            return value;
        }

        return value.substring(0, portSeparatorIndex);
    }

    private static String removeLeadingDot(String value) {
        if (value.startsWith(".")) {
            return value.substring(1);
        }

        return value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
