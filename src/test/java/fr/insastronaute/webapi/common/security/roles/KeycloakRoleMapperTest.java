package fr.insastronaute.webapi.common.security.roles;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRoleMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KeycloakRoleMapper roleMapper = new KeycloakRoleMapper(objectMapper, "inte-backend");

    @Test
    void extractsStudentRoleFromRealmAccessClaims() {
        Map<String, Object> claims = Map.of(
                "realm_access", Map.of(
                        "roles", List.of("STUDENT")
                )
        );

        Set<GrantedAuthority> authorities = roleMapper.extractAuthorities(claims);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_STUDENT");
    }

    @Test
    void extractsStudentRoleFromClientAccessClaims() {
        Map<String, Object> claims = Map.of(
                "resource_access", Map.of(
                        "inte-backend", Map.of(
                                "roles", List.of("STUDENT")
                        )
                )
        );

        Set<GrantedAuthority> authorities = roleMapper.extractAuthorities(claims);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_STUDENT");
    }

    @Test
    void extractsStudentRoleFromAccessTokenPayload() throws JsonProcessingException {
        String accessToken = jwtWithPayload(Map.of(
                "resource_access", Map.of(
                        "inte-backend", Map.of(
                                "roles", List.of("STUDENT")
                        )
                )
        ));

        Set<GrantedAuthority> authorities = roleMapper.extractAuthoritiesFromJwt(accessToken);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_STUDENT");
    }

    @Test
    void extractsStudentLimitedRoleFromLimitedOnlyToken() throws JsonProcessingException {
        String accessToken = jwtWithPayload(Map.of(
                "realm_access", Map.of(
                        "roles", List.of("STUDENT_LIMITED")
                )
        ));

        Set<GrantedAuthority> authorities = roleMapper.extractAuthoritiesFromJwt(accessToken);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_STUDENT_LIMITED");
        assertThat(KeycloakRoleMapper.isApplicationRole("STUDENT_LIMITED")).isTrue();
    }

    @Test
    void preservesStudentLimitedRestrictionAlongsideStudentRole() throws JsonProcessingException {
        String accessToken = jwtWithPayload(Map.of(
                "resource_access", Map.of(
                        "inte-backend", Map.of(
                                "roles", List.of("STUDENT", "STUDENT_LIMITED")
                        )
                )
        ));

        Set<GrantedAuthority> authorities = roleMapper.extractAuthoritiesFromJwt(accessToken);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_STUDENT", "ROLE_STUDENT_LIMITED");
    }

    @Test
    void extractsCurrentApplicationRoles() {
        Map<String, Object> claims = Map.of(
                "realm_access", Map.of(
                        "roles", List.of(
                                "ACCES_SITE_ADMIN",
                                "ADMIN",
                                "RESPOS_DEFIS",
                                "SOUS_RESPO_DEFIS",
                                "RESPO_DEPART",
                                "SOUS_RESPO_DEPART",
                                "ignored-role"
                        )
                )
        );

        Set<GrantedAuthority> authorities = roleMapper.extractAuthorities(claims);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(
                        "ROLE_ACCES_SITE_ADMIN",
                        "ROLE_ADMIN",
                        "ROLE_RESPOS_DEFIS",
                        "ROLE_SOUS_RESPO_DEFIS",
                        "ROLE_RESPO_DEPART",
                        "ROLE_SOUS_RESPO_DEPART"
                );
    }

    @Test
    void ignoresRolesFromOtherClients() {
        Map<String, Object> claims = Map.of(
                "resource_access", Map.of(
                        "other-client", Map.of(
                                "roles", List.of("STUDENT")
                        )
                )
        );

        Set<GrantedAuthority> authorities = roleMapper.extractAuthorities(claims);

        assertThat(authorities).isEmpty();
    }

    private String jwtWithPayload(Map<String, Object> payload) throws JsonProcessingException {
        return encode(Map.of("alg", "none"))
                + "."
                + encode(payload)
                + ".";
    }

    private String encode(Map<String, Object> value) throws JsonProcessingException {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8));
    }
}
