package fr.insastronaute.webapi.common.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La generation de la spec, qui lit les documents sans session.
 *
 * <p>Deux reglages distincts, et il faut les deux : {@code openapi-docs-public} ouvre
 * l'acces, {@code springdoc.api-docs.enabled} publie la route. Rendre public ce qui n'est
 * pas publie ne donne pas un 403, cela donne un 404 -- et le test le rappelle en posant les
 * deux, comme le fait le profil {@code openapi}.</p>
 */
@SpringBootTest(properties = {
        "app.security.openapi-docs-public=true",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiGenerationSecurityTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void makesOnlyOpenApiDocumentsPublicForGeneration() throws Exception {
        mockMvc.perform(get("/v3/api-docs/admin")).andExpect(status().isOk());
    }
}
