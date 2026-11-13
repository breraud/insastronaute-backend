package fr.insastronaute.webapi.common.security.oauth;

import fr.insastronaute.webapi.auth.keycloak.KeycloakDepartmentGroupSynchronizationService;
import fr.insastronaute.webapi.common.security.roles.KeycloakRoleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class SynchronizingOidcUserServiceTest {

    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate = mock(OAuth2UserService.class);
    private final KeycloakRoleMapper roleMapper = mock(KeycloakRoleMapper.class);
    private final KeycloakDepartmentGroupSynchronizationService synchronizationService =
            mock(KeycloakDepartmentGroupSynchronizationService.class);
    private final OidcUserRequest request = mock(OidcUserRequest.class);
    private final OidcUser loadedUser = mock(OidcUser.class);

    @Test
    void synchronizesTheUserLoadedDuringOidcLogin() {
        prepareLoadedUser();
        SynchronizingOidcUserService service =
                new SynchronizingOidcUserService(delegate, roleMapper, synchronizationService);

        OidcUser result = service.loadUser(request);

        verify(synchronizationService).synchronize(org.mockito.ArgumentMatchers.any(OidcUser.class));
        assertThat(result.getSubject()).isEqualTo("user-123");
    }

    @Test
    void keepsLoginSuccessfulWhenSynchronizationUnexpectedlyFails() {
        prepareLoadedUser();
        doThrow(new IllegalStateException("unavailable")).when(synchronizationService).synchronize(org.mockito.ArgumentMatchers.any(OidcUser.class));
        SynchronizingOidcUserService service =
                new SynchronizingOidcUserService(delegate, roleMapper, synchronizationService);

        OidcUser result = service.loadUser(request);

        assertThat(result.getSubject()).isEqualTo("user-123");
    }

    @Test
    void fallsBackToIdTokenWhenDelegateThrowsException() {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken("id-token", now, now.plusSeconds(60), Map.of("sub", "user-fallback"));
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "access-token", now, now.plusSeconds(60), Set.of()
        );

        when(delegate.loadUser(request)).thenThrow(new RuntimeException("Cloudflare challenge / 403 Forbidden"));
        when(request.getIdToken()).thenReturn(idToken);
        when(request.getAccessToken()).thenReturn(accessToken);
        when(roleMapper.extractAuthorities(idToken.getClaims())).thenReturn(Set.of());
        when(roleMapper.extractAuthoritiesFromJwt("access-token")).thenReturn(Set.of());

        SynchronizingOidcUserService service =
                new SynchronizingOidcUserService(delegate, roleMapper, synchronizationService);

        OidcUser result = service.loadUser(request);

        assertThat(result.getSubject()).isEqualTo("user-fallback");
    }

    private void prepareLoadedUser() {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken("id-token", now, now.plusSeconds(60), Map.of("sub", "user-123"));
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "access-token", now, now.plusSeconds(60), Set.of()
        );

        when(delegate.loadUser(request)).thenReturn(loadedUser);
        when(loadedUser.getIdToken()).thenReturn(idToken);
        when(loadedUser.getAuthorities()).thenReturn(Set.of());
        when(loadedUser.getUserInfo()).thenReturn(null);
        when(request.getAccessToken()).thenReturn(accessToken);
        when(roleMapper.extractAuthorities(idToken.getClaims())).thenReturn(Set.of());
        when(roleMapper.extractAuthoritiesFromJwt("access-token")).thenReturn(Set.of());
    }
}
