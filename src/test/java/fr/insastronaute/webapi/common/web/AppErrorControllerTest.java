package fr.insastronaute.webapi.common.web;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppErrorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void rendersBrandedHtmlErrorPageForBrowserRequests() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/missing-page")
                        .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("INSA'Stronaute API")))
                .andExpect(content().string(containsString("Page introuvable")))
                .andExpect(content().string(not(containsString("Whitelabel Error Page"))));
    }

    @Test
    void rendersUnauthorizedHtmlErrorPageWithLoginMessage() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 401)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/challenges")
                        .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Connexion requise")))
                .andExpect(content().string(not(containsString("Page introuvable"))));
    }

    @Test
    void rendersHtmlErrorPageForBrowserNavigationWithoutExplicitAccept() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/missing-page")
                        .header("Sec-Fetch-Mode", "navigate"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Page introuvable")));
    }

    @Test
    @WithMockUser
    void rendersHtmlErrorPageForMissingBrowserRoute() throws Exception {
        mockMvc.perform(get("/missing-page")
                        .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
                        .header("Sec-Fetch-Mode", "navigate"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("INSA'Stronaute API")))
                .andExpect(content().string(containsString("Page introuvable")))
                .andExpect(content().string(containsString("/missing-page")));
    }

    @Test
    void keepsJsonErrorsForApiClients() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/missing-page")
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/missing-page"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void keepsJsonErrorsForMissingApiResources() throws Exception {
        mockMvc.perform(get("/api/missing")
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Ressource introuvable."))
                .andExpect(jsonPath("$.path").value("/api/missing"));
    }

    /**
     * Une panne apres le premier octet : Tomcat inclut cette page dans la reponse deja
     * commencee, puis coupe la connexion. Elle n'a rien a y ajouter - ni corps au fichier,
     * ni seconde erreur au journal.
     */
    @Test
    void aResponseAlreadyUnderwayGetsNothingAppended() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(true);

        assertThat(new AppErrorController().error(request, response)).isNull();
    }
}
