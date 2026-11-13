package fr.insastronaute.webapi.common.security.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toutes les interruptions de connexion ne sont pas des pannes.
 *
 * <p>Un joueur qui refuse les conditions a pris une decision. La traiter comme une erreur
 * serveur produit une trace de cent lignes par clic -- qui noiera les vraies pannes le
 * jour ou elles arrivent -- et renvoie le joueur avec un code qui ne lui apprend rien.</p>
 */
class OAuth2LoginFailureTest {

    /**
     * Le refus se reconnait a la description, pas au code.
     *
     * <p>Keycloak repond {@code access_denied}, qui couvre bien d'autres refus : s'y fier
     * rangerait sous « conditions refusees » des echecs qui n'ont rien a voir.</p>
     */
    @Test
    void recognisesADeclinedTermsPageByItsDescription() {
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
                new OAuth2Error("access_denied", "termsAndConditionsDeclined", null));

        assertThat(OAuth2LoginFailure.reasonOf(exception)).isEqualTo("terms_declined");
        assertThat(OAuth2LoginFailure.isExpected("terms_declined")).isTrue();
    }

    /** Un autre access_denied reste une panne generique : on ne devine pas a sa place. */
    @Test
    void anyOtherAccessDeniedStaysGeneric() {
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
                new OAuth2Error("access_denied", "consent_required", null));

        assertThat(OAuth2LoginFailure.reasonOf(exception)).isEqualTo("oauth2_failure");
        assertThat(OAuth2LoginFailure.isExpected("oauth2_failure")).isFalse();
    }

    /** Le retour arriere du navigateur : attendu depuis toujours, il le reste. */
    @Test
    void keepsTreatingAMissingAuthorizationRequestAsExpected() {
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
                new OAuth2Error("authorization_request_not_found"));

        assertThat(OAuth2LoginFailure.reasonOf(exception)).isEqualTo("authorization_request_not_found");
        assertThat(OAuth2LoginFailure.isExpected("authorization_request_not_found")).isTrue();
    }

    /**
     * Le code d'autorisation rejoue : une page de retour rechargee, un retour arriere
     * dessus, un onglet restaure. Keycloak refuse, et il a raison -- mais ce n'est pas
     * une panne, et cent lignes de trace par clic noieraient celles qui en sont.
     */
    @Test
    void treatsASpentAuthorizationCodeAsExpected() {
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
                new OAuth2Error("invalid_grant", "Code not valid", null));

        assertThat(OAuth2LoginFailure.reasonOf(exception)).isEqualTo("login_code_expired");
        assertThat(OAuth2LoginFailure.isExpected("login_code_expired")).isTrue();
    }

    @Test
    void anythingThatIsNotAnOAuthErrorIsGeneric() {
        assertThat(OAuth2LoginFailure.reasonOf(new BadCredentialsException("nope")))
                .isEqualTo("oauth2_failure");
    }
}
