package fr.insastronaute.webapi.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L'actuator partage le port de l'application. Avant ces regles il tombait sur
 * anyRequest().authenticated() : n'importe quel compte joueur valide pouvait lire
 * /actuator/prometheus, donc les noms de routes, la volumetrie et les internes de
 * la JVM.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void aPlayerCannotReadTheMetricsEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").with(user("joueur").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aStaffAccountThatIsNotAdminCannotReadTheMetricsEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").with(user("sous-respo").roles("SOUS_RESPO_DEFIS")))
                .andExpect(status().isForbidden());
    }

    /**
     * Le matcher est volontairement un fourre-tout : ce que MANAGEMENT_ENDPOINTS
     * exposera demain doit etre couvert sans repasser par la configuration.
     */
    @Test
    void anEndpointNotYetExposedIsAlreadyCoveredByTheCatchAll() throws Exception {
        mockMvc.perform(get("/actuator/env").with(user("joueur").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    /**
     * Un appelant anonyme est renvoye vers Keycloak plutot que refuse sec : c'est le
     * comportement de toute la chaine, et l'important est qu'il ne recoive jamais le
     * corps de la reponse.
     */
    @Test
    void anAnonymousCallerIsSentToLoginRatherThanServedTheMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/keycloak"));
    }

    /**
     * /health garde le niveau qu'il avait : la restriction ne doit pas transformer
     * une sonde de disponibilite en endpoint administrateur.
     */
    @Test
    void healthStaysReadableByAnyAuthenticatedAccount() throws Exception {
        mockMvc.perform(get("/actuator/health").with(user("joueur").roles("STUDENT")))
                .andExpect(status().isOk());
    }
}
