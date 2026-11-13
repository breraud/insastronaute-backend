package fr.insastronaute.webapi.common.security.oauth;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class FrontendRedirectUriResolver {

    private final String frontendBaseUrl;
    private final List<URI> allowedOrigins;

    public FrontendRedirectUriResolver(String frontendBaseUrl) {
        this(frontendBaseUrl, List.of(frontendBaseUrl));
    }

    public FrontendRedirectUriResolver(String frontendBaseUrl, Collection<String> allowedOrigins) {
        this.frontendBaseUrl = removeTrailingSlash(URI.create(frontendBaseUrl).toString());
        this.allowedOrigins = allowedOrigins.stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .map(origin -> URI.create(removeTrailingSlash(origin.trim())))
                .toList();
    }

    public String defaultRedirectUri() {
        return frontendBaseUrl + "/";
    }

    public String resolveOrDefault(String candidate) {
        return resolve(candidate).orElseGet(this::defaultRedirectUri);
    }

    public Optional<String> resolve(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return Optional.empty();
        }

        try {
            URI uri = URI.create(candidate);

            if (uri.isAbsolute()) {
                return hasAllowedOrigin(uri) ? Optional.of(uri.toString()) : Optional.empty();
            }

            if (candidate.startsWith("/") && !candidate.startsWith("//")) {
                return Optional.of(frontendBaseUrl + candidate);
            }

            return Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private boolean hasAllowedOrigin(URI uri) {
        return allowedOrigins.stream().anyMatch(allowedOrigin -> hasSameOrigin(uri, allowedOrigin));
    }

    private static boolean hasSameOrigin(URI candidate, URI allowedOrigin) {
        return Objects.equals(candidate.getScheme(), allowedOrigin.getScheme())
                && Objects.equals(candidate.getHost(), allowedOrigin.getHost())
                && effectivePort(candidate) == effectivePort(allowedOrigin);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }

        return "https".equals(uri.getScheme()) ? 443 : 80;
    }

    private static String removeTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }

        return value;
    }
}
