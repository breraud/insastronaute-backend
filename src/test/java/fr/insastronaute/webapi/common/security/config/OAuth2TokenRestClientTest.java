package fr.insastronaute.webapi.common.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OAuth2TokenRestClientTest {

    @Test
    void preservesIdTokenAsAnAdditionalParameter() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = SecurityConfig.oauth2TokenRestClient(builder);

        server.expect(requestTo("https://keycloak.example/token"))
                .andRespond(withSuccess("""
                        {
                          "access_token": "access-token",
                          "token_type": "Bearer",
                          "expires_in": 300,
                          "id_token": "id-token"
                        }
                        """, MediaType.APPLICATION_JSON));

        OAuth2AccessTokenResponse response = restClient.post()
                .uri("https://keycloak.example/token")
                .retrieve()
                .body(OAuth2AccessTokenResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.getAdditionalParameters()).containsEntry("id_token", "id-token");
        server.verify();
    }
}
