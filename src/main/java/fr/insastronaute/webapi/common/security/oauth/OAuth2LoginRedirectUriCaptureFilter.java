package fr.insastronaute.webapi.common.security.oauth;

import fr.insastronaute.webapi.common.security.config.AppSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public final class OAuth2LoginRedirectUriCaptureFilter extends OncePerRequestFilter {

    public static final String SESSION_ATTRIBUTE = OAuth2LoginRedirectUriCaptureFilter.class.getName() + ".REDIRECT_URI";

    private static final String AUTHORIZATION_REQUEST_PATH = "/oauth2/authorization/keycloak";
    private static final String REDIRECT_URI_PARAMETER = "redirect_uri";

    private final FrontendRedirectUriResolver redirectUriResolver;

    public OAuth2LoginRedirectUriCaptureFilter(AppSecurityProperties securityProperties) {
        this.redirectUriResolver = new FrontendRedirectUriResolver(
                securityProperties.getFrontendBaseUrl(),
                securityProperties.getAllowedOrigins()
        );
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (isOAuth2AuthorizationRequest(request)) {
            redirectUriResolver.resolve(request.getParameter(REDIRECT_URI_PARAMETER))
                    .ifPresent(redirectUri -> request.getSession().setAttribute(SESSION_ATTRIBUTE, redirectUri));
        }

        filterChain.doFilter(request, response);
    }

    private boolean isOAuth2AuthorizationRequest(HttpServletRequest request) {
        return "GET".equals(request.getMethod()) && AUTHORIZATION_REQUEST_PATH.equals(request.getRequestURI());
    }
}
