package fr.insastronaute.webapi.common.security.oauth;

import fr.insastronaute.webapi.common.security.config.AppSecurityProperties;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2LoginSuccessHandlerTest {

    @Test
    void redirectsToExplicitSessionRedirectUriFirst() throws Exception {
        OAuth2LoginSuccessHandler successHandler = new OAuth2LoginSuccessHandler(securityProperties(), java.util.Set.of());
        MockHttpServletRequest callbackRequest = callbackRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        Objects.requireNonNull(callbackRequest.getSession()).setAttribute(
                OAuth2LoginRedirectUriCaptureFilter.SESSION_ATTRIBUTE,
                "https://admin.example.com/challenges"
        );

        successHandler.onAuthenticationSuccess(callbackRequest, response, authentication());

        assertThat(response.getRedirectedUrl()).isEqualTo("https://admin.example.com/challenges");
    }

    @Test
    void redirectsToSavedSwaggerUiRequestWhenNoExplicitRedirectUriExists() throws Exception {
        OAuth2LoginSuccessHandler successHandler = new OAuth2LoginSuccessHandler(securityProperties(), java.util.Set.of());
        HttpSession session = saveOriginalRequest("/swagger-ui/index.html");
        MockHttpServletRequest callbackRequest = callbackRequest(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(callbackRequest, response, authentication());

        assertThat(response.getRedirectedUrl()).isEqualTo("https://api.example.com/swagger-ui/index.html");
    }

    @Test
    void redirectsToSavedOpenApiRequestWhenNoExplicitRedirectUriExists() throws Exception {
        OAuth2LoginSuccessHandler successHandler = new OAuth2LoginSuccessHandler(securityProperties(), java.util.Set.of());
        HttpSession session = saveOriginalRequest("/v3/api-docs");
        MockHttpServletRequest callbackRequest = callbackRequest(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(callbackRequest, response, authentication());

        assertThat(response.getRedirectedUrl()).isEqualTo("https://api.example.com/v3/api-docs");
    }

    @Test
    void ignoresSavedNonDocumentationRequests() throws Exception {
        OAuth2LoginSuccessHandler successHandler = new OAuth2LoginSuccessHandler(securityProperties(), java.util.Set.of());
        HttpSession session = saveOriginalRequest("/api/challenges");
        MockHttpServletRequest callbackRequest = callbackRequest(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(callbackRequest, response, authentication());

        assertThat(response.getRedirectedUrl()).isEqualTo("https://example.com/");
    }

    private static AppSecurityProperties securityProperties() {
        AppSecurityProperties properties = new AppSecurityProperties();

        properties.setFrontendBaseUrl("https://example.com");

        return properties;
    }

    private static HttpSession saveOriginalRequest(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.setScheme("https");
        request.setServerName("api.example.com");
        request.setServerPort(443);
        request.setRequestURI(path);
        request.setServletPath(path);

        new HttpSessionRequestCache().saveRequest(request, response);

        return request.getSession();
    }

    private static MockHttpServletRequest callbackRequest() {
        return callbackRequest(null);
    }

    private static MockHttpServletRequest callbackRequest(HttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/keycloak");

        if (session != null) {
            request.setSession(session);
        }

        return request;
    }

    private static TestingAuthenticationToken authentication() {
        return new TestingAuthenticationToken("admin", "password", "ROLE_ADMIN");
    }
}
