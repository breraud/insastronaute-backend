package fr.insastronaute.webapi.auth;

import fr.insastronaute.webapi.auth.keycloak.KeycloakAdminGroupMembershipGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshRolesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Mocke sur la classe concrete plutot que sur l'interface KeycloakRealmRoleGateway :
    // cette classe implemente aussi KeycloakGroupMembershipGateway, dont dependent
    // d'autres composants (LeaderboardService, KeycloakDepartmentGroupSynchronizationService).
    // Un mock de la seule interface KeycloakRealmRoleGateway ne les satisferait pas, et
    // le contexte echouerait au demarrage.
    @MockitoBean
    private KeycloakAdminGroupMembershipGateway keycloakAdminGroupMembershipGateway;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void anAuthenticatedPlayerGetsTheirRolesRefreshed() throws Exception {
        when(keycloakAdminGroupMembershipGateway.listRealmRoleNames(any())).thenReturn(List.of("STUDENT"));

        mockMvc.perform(post("/api/auth/refresh-roles").with(oidcLogin()).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void refreshKeepsStudentLimitedForLimitedOnlyAndMixedRoleUsers() throws Exception {
        assertRefreshedAuthorities(
                List.of("STUDENT_LIMITED"),
                List.of("ROLE_STUDENT_LIMITED")
        );
        assertRefreshedAuthorities(
                List.of("STUDENT", "STUDENT_LIMITED"),
                List.of("ROLE_STUDENT", "ROLE_STUDENT_LIMITED")
        );
    }

    @Test
    void anAnonymousCallerIsRefused() throws Exception {
        mockMvc.perform(post("/api/auth/refresh-roles").with(csrf()))
                .andExpect(status().is4xxClientError());
    }

    /**
     * Une relecture ratee ne doit pas se faire passer pour une reussite. Le front
     * n'appelle cet endpoint qu'une fois par decision : s'il recevait 204 sur un
     * echec, il cesserait de reessayer et laisserait le joueur devant son ecran
     * d'attente sur un compte pourtant valide.
     */
    @Test
    void aFailedRoleLookupIsReportedRatherThanPassedOffAsASuccess() throws Exception {
        when(keycloakAdminGroupMembershipGateway.listRealmRoleNames(any()))
                .thenThrow(new IllegalStateException("Keycloak injoignable"));

        mockMvc.perform(post("/api/auth/refresh-roles").with(oidcLogin()).with(csrf()))
                .andExpect(status().is5xxServerError());
    }

    private void assertRefreshedAuthorities(List<String> keycloakRoles, List<String> expectedAuthorities)
            throws Exception {
        when(keycloakAdminGroupMembershipGateway.listRealmRoleNames(any())).thenReturn(keycloakRoles);
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/auth/refresh-roles")
                        .with(oidcLogin())
                        .with(csrf())
                        .session(session))
                .andExpect(status().isNoContent());

        SecurityContext refreshed = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
        );
        assertThat(refreshed.getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .filteredOn(authority -> authority.startsWith("ROLE_"))
                .containsExactlyInAnyOrderElementsOf(expectedAuthorities);
    }
}
