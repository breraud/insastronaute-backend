package fr.insastronaute.webapi.auth;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class KeycloakAccountController {

    private final URI accountUri;

    public KeycloakAccountController(
            @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri}") String issuerUri
    ) {
        String normalizedIssuer = issuerUri.strip().replaceAll("/+$", "");
        this.accountUri = URI.create(normalizedIssuer + "/account/");
    }

    @GetMapping("/api/account")
    @Operation(summary = "Open account management", description = "Redirects the authenticated user to the configured Keycloak account console.")
    public ResponseEntity<Void> redirectToAccount() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(accountUri)
                .build();
    }
}
