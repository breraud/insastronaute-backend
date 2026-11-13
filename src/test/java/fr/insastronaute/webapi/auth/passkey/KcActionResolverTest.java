package fr.insastronaute.webapi.auth.passkey;

import fr.insastronaute.webapi.common.security.oauth.KcActionAuthorizationRequestResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le parametre qui declenche une action Keycloak au passage.
 *
 * <p>La lecon derriere ces tests : fabriquer la requete d'autorisation a la main
 * paraissait plus direct et ne marchait pas. Le client ne declare qu'une URI de retour,
 * son propre callback, et Keycloak refuse toute autre avec
 * <em>Invalid parameter: redirect_uri</em> - ce qui est le comportement souhaitable, une
 * URI de retour librement choisie etant un redirecteur ouvert. La requete doit donc
 * rester celle de Spring, a laquelle on ajoute un parametre.</p>
 */
class KcActionResolverTest {

    private final KcActionAuthorizationRequestResolver resolver =
            new KcActionAuthorizationRequestResolver(
                    new InMemoryClientRegistrationRepository(keycloak()), "/oauth2/authorization");

    @Test
    void aKnownActionTravelsWithTheRequest() {
        OAuth2AuthorizationRequest request = resolver.resolve(
                authorizationRequestWith("webauthn-register-passwordless"));

        assertThat(request).isNotNull();
        assertThat(request.getAdditionalParameters()).containsEntry("kc_action", "webauthn-register-passwordless");
        // Et l'URI de retour reste celle du client, jamais une autre.
        assertThat(request.getRedirectUri()).contains("/login/oauth2/code/keycloak");
    }

    /**
     * La liste fermee n'est pas une precaution de style : sans elle, n'importe qui
     * pourrait forcer n'importe quelle action requise de Keycloak sur sa propre session,
     * dont la suppression de compte.
     */
    @Test
    void anUnknownActionIsIgnoredRatherThanForwarded() {
        OAuth2AuthorizationRequest request = resolver.resolve(authorizationRequestWith("delete_account"));

        assertThat(request).isNotNull();
        assertThat(request.getAdditionalParameters()).doesNotContainKey("kc_action");
    }

    /** Sans parametre, le chemin de connexion ordinaire ne change pas d'un octet. */
    @Test
    void anOrdinaryLoginIsLeftUntouched() {
        MockHttpServletRequest plain = new MockHttpServletRequest("GET", "/oauth2/authorization/keycloak");
        plain.setServletPath("/oauth2/authorization/keycloak");

        OAuth2AuthorizationRequest request = resolver.resolve(plain);

        assertThat(request).isNotNull();
        assertThat(request.getAdditionalParameters()).doesNotContainKey("kc_action");
    }

    private static MockHttpServletRequest authorizationRequestWith(String action) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/keycloak");
        request.setServletPath("/oauth2/authorization/keycloak");
        request.setParameter("kc_action", action);
        return request;
    }

    private static ClientRegistration keycloak() {
        return ClientRegistration.withRegistrationId("keycloak")
                .clientId("inte-backend")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid")
                .authorizationUri("http://localhost:8081/realms/inte/protocol/openid-connect/auth")
                .tokenUri("http://localhost:8081/realms/inte/protocol/openid-connect/token")
                .userNameAttributeName("sub")
                .build();
    }
}
