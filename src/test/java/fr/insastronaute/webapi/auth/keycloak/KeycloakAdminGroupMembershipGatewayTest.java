package fr.insastronaute.webapi.auth.keycloak;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KeycloakAdminGroupMembershipGatewayTest {

    @org.junit.jupiter.api.AfterEach
    void stopTravelling() {
        fr.insastronaute.webapi.common.timetravel.TimeTravelContext.clear();
    }

    /**
     * Le voyage dans le temps ne date pas le cache du jeton d'administration.
     *
     * <p>Ces echeances decrivent la duree de vie d'un jeton emis par Keycloak, qui ignore
     * tout de nos voyages. Datees a l'heure du jeu, un membre du staff projete dans le
     * futur les repoussait la-bas : la garde trouvait le jeton valable pour toujours, et
     * chaque appel repartait avec un jeton reellement perime — 401, pour tout le monde,
     * jusqu'au redemarrage. Le cache est partage, un seul voyageur suffisait.</p>
     */
    @Test
    void aTravellerDoesNotFreezeTheAdminTokenCacheInTheFuture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminGroupMembershipGateway gateway = new KeycloakAdminGroupMembershipGateway(
                builder.build(),
                "https://id.example.test/auth/realms/inte",
                "inte-backend",
                "top-secret"
        );

        // Deux jetons de suite : le premier expire en une seconde, le second doit donc
        // etre redemande au deuxieme appel.
        server.expect(org.springframework.test.web.client.ExpectedCount.twice(),
                        requestTo("https://id.example.test/auth/realms/inte/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"admin-token\",\"expires_in\":1}", MediaType.APPLICATION_JSON));
        server.expect(org.springframework.test.web.client.ExpectedCount.twice(),
                        requestTo("https://id.example.test/auth/admin/realms/inte/users/user-123/groups?briefRepresentation=true&max=1000"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // Un membre du staff se projette dans le futur, et son appel remplit le cache.
        fr.insastronaute.webapi.common.timetravel.TimeTravelContext.setForcedTime(
                java.time.Instant.now().plus(java.time.Duration.ofDays(30)));
        gateway.listUserGroups("user-123");

        // Tout le monde revient a l'heure reelle. Le jeton d'une seconde est perime.
        fr.insastronaute.webapi.common.timetravel.TimeTravelContext.clear();
        gateway.listUserGroups("user-123");

        // Sans le correctif, le second appel reprenait le jeton du cache et n'en
        // redemandait aucun : la verification tombe sur le jeton manquant.
        server.verify();
    }

    @Test
    void usesClientCredentialsAndAdminGroupEndpoints() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminGroupMembershipGateway gateway = new KeycloakAdminGroupMembershipGateway(
                builder.build(),
                "https://id.example.test/auth/realms/inte",
                "inte-backend",
                "top-secret"
        );

        server.expect(once(), requestTo("https://id.example.test/auth/realms/inte/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("grant_type=client_credentials")))
                .andExpect(content().string(containsString("client_id=inte-backend")))
                .andRespond(withSuccess("{\"access_token\":\"admin-token\",\"expires_in\":60}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://id.example.test/auth/admin/realms/inte/groups?briefRepresentation=true&max=1000"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":\"iti-id\",\"name\":\"ITI\"}]", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://id.example.test/auth/admin/realms/inte/users/user-123/groups?briefRepresentation=true&max=1000"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":\"cfi-id\",\"name\":\"CFI\"}]", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://id.example.test/auth/admin/realms/inte/users/user-123/groups/iti-id"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());
        server.expect(once(), requestTo("https://id.example.test/auth/admin/realms/inte/users/user-123/groups/cfi-id"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        List<KeycloakGroupMembershipGateway.Group> realmGroups = gateway.listRealmGroups();
        List<KeycloakGroupMembershipGateway.Group> userGroups = gateway.listUserGroups("user-123");
        gateway.joinGroup("user-123", "iti-id");
        gateway.leaveGroup("user-123", "cfi-id");

        assertThat(realmGroups).containsExactly(new KeycloakGroupMembershipGateway.Group("iti-id", "ITI"));
        assertThat(userGroups).containsExactly(new KeycloakGroupMembershipGateway.Group("cfi-id", "CFI"));
        server.verify();
    }

    @Test
    void flattensNestedRealmGroups() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminGroupMembershipGateway gateway = new KeycloakAdminGroupMembershipGateway(
                builder.build(), "https://id.example.test/realms/inte", "client", "secret"
        );

        server.expect(requestTo("https://id.example.test/realms/inte/protocol/openid-connect/token"))
                .andRespond(withSuccess("{\"access_token\":\"token\",\"expires_in\":60}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://id.example.test/admin/realms/inte/groups?briefRepresentation=true&max=1000"))
                .andRespond(withSuccess("[{\"id\":\"parent\",\"name\":\"Departments\",\"subGroups\":[{\"id\":\"gm\",\"name\":\"GM\"}]}]", MediaType.APPLICATION_JSON));

        assertThat(gateway.listRealmGroups()).contains(
                new KeycloakGroupMembershipGateway.Group("parent", "Departments"),
                new KeycloakGroupMembershipGateway.Group("gm", "GM")
        );
        server.verify();
    }
}
