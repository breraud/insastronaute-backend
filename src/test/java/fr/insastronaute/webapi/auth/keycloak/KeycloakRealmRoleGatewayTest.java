package fr.insastronaute.webapi.auth.keycloak;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class KeycloakRealmRoleGatewayTest {

    private static final String TOKEN_URI = "https://id.example.test/auth/realms/inte/protocol/openid-connect/token";
    private static final String ROLE_URI = "https://id.example.test/auth/admin/realms/inte/roles/STUDENT";
    private static final String USER_MAPPINGS_URI =
            "https://id.example.test/auth/admin/realms/inte/users/u1/role-mappings/realm";

    private KeycloakAdminGroupMembershipGateway gateway;
    private MockRestServiceServer server;

    private void newGateway() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new KeycloakAdminGroupMembershipGateway(
                builder.build(),
                "https://id.example.test/auth/realms/inte",
                "inte-backend",
                "top-secret"
        );
    }

    private void expectToken() {
        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"admin-token\",\"expires_in\":60}", MediaType.APPLICATION_JSON));
    }

    /**
     * La racine d'administration, quand elle est donnee, remplace l'emetteur public.
     *
     * <p>En production celui-ci est derriere Cloudflare, dont le defi « managed » attend un
     * navigateur : la demande de jeton -- de serveur a serveur -- recevait une page HTML en
     * 403, et l'annuaire du staff restait vide. Le jeton *et* l'appel d'administration
     * doivent passer par la porte interne : le premier seul ne suffirait pas.</p>
     */
    @Test
    void anExplicitAdminBaseUrlDivertsBothTheTokenAndTheAdminCall() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer internal = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminGroupMembershipGateway internalGateway = new KeycloakAdminGroupMembershipGateway(
                builder.build(),
                "https://id.example.test/auth/realms/inte",
                "http://keycloak:8080",
                "inte-backend",
                "top-secret"
        );

        internal.expect(requestTo("http://keycloak:8080/realms/inte/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"admin-token\",\"expires_in\":60}", MediaType.APPLICATION_JSON));
        internal.expect(once(), requestTo("http://keycloak:8080/admin/realms/inte/roles/ADMIN/users?max=2000"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":\"u1\"}]", MediaType.APPLICATION_JSON));

        assertThat(internalGateway.listUsersInRealmRole("ADMIN")).contains(List.of("u1"));
        internal.verify();
    }

    /**
     * Racine vide : rien ne bouge. `serverBase + "/realms/" + realm` reconstitue l'emetteur
     * au caractere pres, et c'est ce que verifient tous les autres cas de cette classe.
     */
    @Test
    void anEmptyAdminBaseUrlKeepsTheIssuerDerivedEndpoints() {
        newGateway();
        expectToken();
        server.expect(once(), requestTo("https://id.example.test/auth/admin/realms/inte/roles/ADMIN/users?max=2000"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(gateway.listUsersInRealmRole("ADMIN")).contains(List.of());
        server.verify();
    }

    @Test
    void assignRealmRoleLooksUpTheRoleThenPostsIt() {
        newGateway();
        expectToken();
        server.expect(once(), requestTo(ROLE_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"role-1\",\"name\":\"STUDENT\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(USER_MAPPINGS_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("role-1")))
                .andExpect(content().string(containsString("STUDENT")))
                .andRespond(withSuccess());

        gateway.assignRealmRole("u1", "STUDENT");

        server.verify();
    }

    @Test
    void removeRealmRoleSendsTheSameBodyOnDelete() {
        newGateway();
        expectToken();
        server.expect(once(), requestTo(ROLE_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"role-1\",\"name\":\"STUDENT\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(USER_MAPPINGS_URI))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(content().string(containsString("role-1")))
                .andExpect(content().string(containsString("STUDENT")))
                .andRespond(withSuccess());

        gateway.removeRealmRole("u1", "STUDENT");

        server.verify();
    }

    @Test
    void listRealmRoleNamesReadsTheUsersMappings() {
        newGateway();
        expectToken();
        server.expect(once(), requestTo(USER_MAPPINGS_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "[{\"id\":\"role-1\",\"name\":\"STUDENT\"},{\"id\":\"role-2\",\"name\":\"ADMIN\"}]",
                        MediaType.APPLICATION_JSON));

        List<String> roleNames = gateway.listRealmRoleNames("u1");

        assertThat(roleNames).containsExactly("STUDENT", "ADMIN");
        server.verify();
    }

    @Test
    void theRoleDefinitionIsLookedUpOnlyOnce() {
        newGateway();
        expectToken();
        server.expect(once(), requestTo(ROLE_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"role-1\",\"name\":\"STUDENT\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(USER_MAPPINGS_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
        server.expect(once(), requestTo(USER_MAPPINGS_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        gateway.assignRealmRole("u1", "STUDENT");
        gateway.assignRealmRole("u1", "STUDENT");

        server.verify();
    }

    @Test
    void aMissingRoleFailsWithItsName() {
        newGateway();
        expectToken();
        server.expect(once(), requestTo(ROLE_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> gateway.assignRealmRole("u1", "STUDENT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STUDENT");

        server.verify();
    }

    @Test
    void aForbiddenRoleLookupDoesNotClaimTheRoleIsMissing() {
        newGateway();
        expectToken();
        server.expect(once(), requestTo(ROLE_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> gateway.assignRealmRole("u1", "STUDENT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STUDENT")
                .hasMessageNotContaining("introuvable");

        server.verify();
    }
}
