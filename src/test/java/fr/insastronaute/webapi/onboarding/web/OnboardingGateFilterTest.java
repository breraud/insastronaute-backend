package fr.insastronaute.webapi.onboarding.web;

import fr.insastronaute.webapi.common.security.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OnboardingGateFilterTest {

    private static final String PROBE = "/api/probe-onboarding-gate";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void aStudentPassesThrough() throws Exception {
        mockMvc.perform(get(PROBE).with(user("player").roles("STUDENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aStudentLimitedPassesThroughWithoutStudentRole() throws Exception {
        mockMvc.perform(get(PROBE).with(user("limited").roles("STUDENT_LIMITED")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aStudentLimitedDoesNotGainPanelAdminAccess() throws Exception {
        mockMvc.perform(get("/api/admin/test").with(user("limited").roles("STUDENT_LIMITED")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAuthenticatedUserWithoutStudentIsRefusedWithAStableCode() throws Exception {
        mockMvc.perform(get(PROBE).with(user("newcomer")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ONBOARDING_REQUIRED"))
                .andExpect(jsonPath("$.path").value(PROBE));
    }

    @Test
    void everyStaffRolePassesThroughWithoutStudent() throws Exception {
        for (String role : SecurityConfig.SITE_BYPASS_ROLES) {
            mockMvc.perform(get(PROBE).with(user("staff").roles(role)))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void theOnboardingPathsStayOpenToSomeoneWhoHasNotDoneIt() throws Exception {
        // Le controleur attend un OidcUser que `user()` ne fournit pas, donc l'appel
        // n'ira pas jusqu'au bout. Ce qui compte ici est seulement que le garde ne
        // soit pas ce qui l'arrete.
        mockMvc.perform(get("/api/onboarding/state").with(user("newcomer")))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
        mockMvc.perform(get("/api/csrf")).andExpect(status().isOk());
        mockMvc.perform(get("/api/session")).andExpect(status().isOk());
    }

    @Test
    void theProfileEndpointIsNotWhitelisted() throws Exception {
        mockMvc.perform(get("/api/users/profile").with(user("newcomer")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aPublicEndpointStaysPublicForAnonymousCallers() throws Exception {
        // /api/scan est en permitAll : un appelant anonyme n'a rien a voir avec
        // l'accueil, et le garde ne doit pas lui retirer une page qu'il pouvait
        // ouvrir hier.
        mockMvc.perform(get("/api/scan"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
    }
}
