package fr.insastronaute.webapi.common.security.config;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le schema de l'API est ferme tant qu'on ne l'ouvre pas.
 *
 * <p>Il n'etait pas anonyme -- les routes exigeaient une session -- mais n'importe quel
 * compte connecte pouvait lire la liste exacte des routes et de leurs formats. En
 * production, ce n'est pas un outil, c'est un plan.</p>
 *
 * <p>Ferme par defaut, et non ouvert : les deux oublis n'ont pas le meme prix, et la valeur
 * par defaut doit suivre le plus cher. Ce test tient ce choix -- sans lui, un `enabled=true`
 * pose un jour pour depanner resterait, et personne ne le verrait.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SwaggerClosedByDefaultTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    /**
     * 404 et non 403 : desactive, springdoc ne publie plus la route du tout. La difference
     * compte -- un 403 dit qu'il y a quelque chose derriere.
     */
    @Test
    void anAuthenticatedAccountFindsNoSchema() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(user("student").roles("STUDENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void anAdministratorFindsNoSchemaEither() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void theExplorerIsGoneToo() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }
}
