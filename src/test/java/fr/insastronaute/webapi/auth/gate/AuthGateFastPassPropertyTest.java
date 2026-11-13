package fr.insastronaute.webapi.auth.gate;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le prefixe des proprietes du portail.
 *
 * Une propriete ecrite sous le mauvais prefixe n'echoue pas : elle est ignoree, et le code
 * de passage prioritaire reste vide -- donc desactive -- sans que rien ne le dise. On s'en
 * apercevrait le matin ou quelqu'un essaie de s'en servir.
 */
class AuthGateFastPassPropertyTest {

    @Test
    void thePropertyFileUsesThePrefixTheClassDeclares() {
        String prefix = AuthGateProperties.class.getAnnotation(ConfigurationProperties.class).prefix();

        assertThat(prefix).isEqualTo("app.auth-gate");
    }

    @Test
    void aBlankCodeLeavesTheShortcutClosed() {
        AuthGateProperties properties = new AuthGateProperties();

        assertThat(properties.getFastPassCode()).isBlank();
    }
}
