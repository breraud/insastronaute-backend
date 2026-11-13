package fr.insastronaute.webapi.common.security;

import fr.insastronaute.webapi.onboarding.service.ModeratorGenderGateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.security.domain=example.com"
})
class CsrfControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Ce test porte sur les matchers de roles, pas sur le garde du genre.
     *
     * <p>Depuis qu'il existe, la moderation exige aussi un genre declare : sans cette reponse,
     * les roles de moderation seraient refuses pour une raison qui n'est pas celle qu'on
     * mesure ici. Le garde a son propre test.</p>
     */
    @MockitoBean
    private ModeratorGenderGateService moderatorGenderGateService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void csrfEndpointReturnsCurrentToken() throws Exception {
        mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parameterName").value("_csrf"))
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.token").value(not(blankOrNullString())));
    }

    @Test
    void csrfEndpointAllowsConfiguredOriginWithCredentials() throws Exception {
        mockMvc.perform(get("/api/csrf")
                        .header(HttpHeaders.ORIGIN, "https://app.example.com"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.example.com"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.VARY, "Origin"));
    }

    @Test
    void sessionPreflightAllowsAppOrigin() throws Exception {
        mockMvc.perform(options("/api/session")
                        .header(HttpHeaders.ORIGIN, "https://app.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.example.com"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void protectedApiReturnsUnauthorizedForAnonymousUsersWithoutRedirect() throws Exception {
        mockMvc.perform(get("/api/challenges"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void protectedApiRendersUnauthorizedErrorPageForBrowserNavigation() throws Exception {
        mockMvc.perform(get("/api/challenges")
                        .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
                        .header("Sec-Fetch-Mode", "navigate"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(forwardedUrl("/error"));
    }

    @Test
    void adminApiRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/test")
                        .with(user("access-admin").roles("ACCES_SITE_ADMIN")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/test")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void moderationApiAllowsSubmissionModerationRoles() throws Exception {
        when(moderatorGenderGateService.hasChosenGender(any())).thenReturn(true);

        mockMvc.perform(get("/api/moderation/test")
                        .with(user("respo-defis").roles("RESPOS_DEFIS")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/moderation/test")
                        .with(user("sous-respo-defis").roles("SOUS_RESPO_DEFIS")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/moderation/test")
                        .with(user("respo-depart").roles("RESPO_DEPART")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/moderation/test")
                        .with(user("sous-respo-depart").roles("SOUS_RESPO_DEPART")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/moderation/test")
                        .with(user("student").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderationHistoryExcludesDepartmentManagers() throws Exception {
        when(moderatorGenderGateService.hasChosenGender(any())).thenReturn(true);

        mockMvc.perform(get("/api/moderation/history/test")
                        .with(user("respo-defis").roles("RESPOS_DEFIS")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/moderation/history/test")
                        .with(user("respo-depart").roles("RESPO_DEPART")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/moderation/history/test")
                        .with(user("sous-respo-depart").roles("SOUS_RESPO_DEPART")))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfRemainsRequiredOnProtectedMutatingEndpoints() throws Exception {
        mockMvc.perform(post("/api/admin/challenges")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutAcceptsValidCsrfHeader() throws Exception {
        mockMvc.perform(post("/logout")
                        .with(user("user"))
                        .with(csrf().asHeader()))
                .andExpect(status().is3xxRedirection());
    }
}
