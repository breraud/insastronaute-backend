package fr.insastronaute.webapi.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ce que Swagger laisse voir **quand il est ouvert**.
 *
 * Il est desormais ferme par defaut : oublier de l'ouvrir en test coute un outil, oublier
 * de le fermer en production coute la liste exacte des routes et de leurs formats. Ces
 * tests decrivent donc l'etat ouvert, et le disent en l'activant eux-memes -- sans quoi ils
 * decriraient un comportement que la configuration ne produit plus.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",
})
class SwaggerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void openApiDocsRedirectAnonymousUsersToKeycloakLogin() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/keycloak"));
    }

    @Test
    void openApiDocsAllowAuthenticatedUsers() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .with(user("student").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.info.title").value("INSAstronaute Web API"));
    }

    @Test
    void openApiDocsAllowAdminUsers() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.info.title").value("INSAstronaute Web API"));
    }

    @Test
    void appOpenApiDocsIncludePlayerPlaceEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs/app")
                        .with(user("student").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/place/canvas'].get").exists())
                .andExpect(jsonPath("$.paths['/api/place/pixel'].post").exists());
    }

    @Test
    void swaggerUiAllowsAuthenticatedUsers() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")
                        .with(user("student").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void swaggerUiAllowsAdminUsers() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }
}
