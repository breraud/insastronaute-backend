package fr.insastronaute.webapi.common.security.csrf;

public record CsrfTokenResponse(
        String parameterName,
        String headerName,
        String token
) {
}
