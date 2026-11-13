package fr.insastronaute.webapi.push.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Qui peut se servir du panneau de notifications.
 *
 * <p>L'envoi de test vise toujours son appelant, ce qui avait fait juger la route
 * inoffensive et l'avait ouverte a tout le staff. Mais le panneau ne se limite pas a
 * l'envoi : {@code /devices} enumere les appareils, et l'ensemble sert a eprouver la
 * chaine de notifications - un outil d'exploitation, pas un jouet de responsable.</p>
 *
 * <p>Ces tests epinglent la regle au niveau de la chaine de securite, pas du controleur :
 * la route n'a aucune annotation, elle ne tient que par {@code SecurityConfig}. Une
 * derogation ajoutee la-bas passerait autrement inapercue.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminPushControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    // Sans ces deux-la, le contexte va interroger le vrai Keycloak au demarrage. Il repond
    // sur la machine d'un developpeur, jamais sur un agent d'integration : le test passerait
    // ici et casserait la publication de l'image.
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void aStaffAccountThatIsNotAdminCannotSendATestNotification() throws Exception {
        mockMvc.perform(post("/api/admin/push/test")
                        .with(user("sous-respo").roles("SOUS_RESPO_DEFIS"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aStaffAccountThatIsNotAdminCannotListTheDevices() throws Exception {
        mockMvc.perform(get("/api/admin/push/devices")
                        .with(user("respo-event").roles("RESPOS_EVENT")))
                .andExpect(status().isForbidden());
    }

    /**
     * L'administrateur, lui, passe la barriere. La route echoue ensuite pour ses propres
     * raisons - le compte simule n'existe pas en base - et c'est justement la preuve que
     * l'autorisation, elle, a ete accordee.
     */
    @Test
    void anAdministratorGetsPastTheBarrier() throws Exception {
        mockMvc.perform(get("/api/admin/push/devices")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 403) {
                        throw new AssertionError("L'administrateur a ete refuse par la chaine de securite.");
                    }
                });
    }

    @Test
    void anAnonymousVisitorIsTurnedAway() throws Exception {
        mockMvc.perform(get("/api/admin/push/devices"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status != 401 && status != 403) {
                        throw new AssertionError("Visiteur anonyme admis avec le statut " + status);
                    }
                });
    }
}
