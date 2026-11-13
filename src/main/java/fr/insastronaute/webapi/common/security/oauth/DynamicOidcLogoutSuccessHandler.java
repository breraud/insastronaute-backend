package fr.insastronaute.webapi.common.security.oauth;

import fr.insastronaute.webapi.common.security.config.AppSecurityProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

public final class DynamicOidcLogoutSuccessHandler implements LogoutSuccessHandler {

    public static final String POST_LOGOUT_REDIRECT_COOKIE = "POST_LOGOUT_REDIRECT_URI";

    private static final String REDIRECT_URI_PARAMETER = "redirect_uri";
    private static final String KEYCLOAK_POST_LOGOUT_CALLBACK = "{baseUrl}/logout/success";

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final FrontendRedirectUriResolver redirectUriResolver;

    public DynamicOidcLogoutSuccessHandler(
            ClientRegistrationRepository clientRegistrationRepository,
            AppSecurityProperties securityProperties
    ) {
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.redirectUriResolver = new FrontendRedirectUriResolver(
                securityProperties.getFrontendBaseUrl(),
                securityProperties.getAllowedOrigins()
        );
    }

    @Override
    public void onLogoutSuccess(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        Optional<String> redirectUri = redirectUriResolver.resolve(request.getParameter(REDIRECT_URI_PARAMETER));
        redirectUri.ifPresent(value ->
                response.addHeader(HttpHeaders.SET_COOKIE, postLogoutRedirectCookie(value).toString())
        );

        OidcClientInitiatedLogoutSuccessHandler delegate =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);

        delegate.setPostLogoutRedirectUri(KEYCLOAK_POST_LOGOUT_CALLBACK);
        delegate.onLogoutSuccess(request, response, authentication);
    }

    private static ResponseCookie postLogoutRedirectCookie(String redirectUri) {
        return ResponseCookie.from(POST_LOGOUT_REDIRECT_COOKIE, encode(redirectUri))
                .httpOnly(true)
                .sameSite("Lax")
                .path("/logout/success")
                .maxAge(Duration.ofMinutes(2))
                .build();
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
