package fr.insastronaute.webapi.common.security.oauth;

import fr.insastronaute.webapi.common.security.config.AppSecurityProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

@Controller
public class LogoutSuccessController {

    private final FrontendRedirectUriResolver redirectUriResolver;

    public LogoutSuccessController(
            AppSecurityProperties securityProperties
    ) {
        this.redirectUriResolver = new FrontendRedirectUriResolver(
                securityProperties.getFrontendBaseUrl(),
                securityProperties.getAllowedOrigins()
        );
    }

    @GetMapping("/logout/success")
    public String logoutSuccess(
            @CookieValue(
                    name = DynamicOidcLogoutSuccessHandler.POST_LOGOUT_REDIRECT_COOKIE,
                    required = false
            ) String encodedRedirectUri,
            HttpServletResponse response
    ) {
        response.addHeader(HttpHeaders.SET_COOKIE, expiredPostLogoutRedirectCookie().toString());

        String redirectUri = decode(encodedRedirectUri)
                .flatMap(redirectUriResolver::resolve)
                .orElse("/");

        return "redirect:" + redirectUri;
    }

    private static ResponseCookie expiredPostLogoutRedirectCookie() {
        return ResponseCookie.from(DynamicOidcLogoutSuccessHandler.POST_LOGOUT_REDIRECT_COOKIE, "")
                .httpOnly(true)
                .sameSite("Lax")
                .path("/logout/success")
                .maxAge(Duration.ZERO)
                .build();
    }

    private static Optional<String> decode(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
