package fr.insastronaute.webapi.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakAccountControllerTest {

    @Test
    void redirectsToTheAccountConsoleDerivedFromTheConfiguredIssuer() {
        KeycloakAccountController controller = new KeycloakAccountController(
                "https://keycloak.example/realms/insastronaute/");

        ResponseEntity<Void> response = controller.redirectToAccount();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("https://keycloak.example/realms/insastronaute/account/");
    }
}
