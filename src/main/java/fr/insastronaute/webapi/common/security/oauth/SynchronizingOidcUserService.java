package fr.insastronaute.webapi.common.security.oauth;

import fr.insastronaute.webapi.auth.keycloak.KeycloakDepartmentGroupSynchronizationService;
import fr.insastronaute.webapi.common.security.roles.KeycloakRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.HashSet;
import java.util.Set;

public class SynchronizingOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SynchronizingOidcUserService.class);

    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;
    private final KeycloakRoleMapper roleMapper;
    private final KeycloakDepartmentGroupSynchronizationService synchronizationService;

    public SynchronizingOidcUserService(
            OAuth2UserService<OidcUserRequest, OidcUser> delegate,
            KeycloakRoleMapper roleMapper,
            KeycloakDepartmentGroupSynchronizationService synchronizationService
    ) {
        this.delegate = delegate;
        this.roleMapper = roleMapper;
        this.synchronizationService = synchronizationService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser;
        if (userRequest.getIdToken() != null && userRequest.getIdToken().getClaims() != null) {
            Set<GrantedAuthority> authorities = extractAuthorities(userRequest);
            oidcUser = new DefaultOidcUser(authorities, userRequest.getIdToken());
        } else {
            try {
                oidcUser = delegate.loadUser(userRequest);
            } catch (Exception exception) {
                LOGGER.warn("Failed to load OidcUser from delegate (e.g. UserInfo endpoint unreachable/blocked), falling back to ID token claims: {}", exception.getMessage());
                Set<GrantedAuthority> authorities = extractAuthorities(userRequest);
                oidcUser = new DefaultOidcUser(authorities, userRequest.getIdToken());
            }
        }

        try {
            synchronizationService.synchronize(oidcUser);
        } catch (RuntimeException exception) {
            LOGGER.warn("Unexpected failure while synchronizing Keycloak groups for user {}", oidcUser.getSubject(), exception);
        }

        Set<GrantedAuthority> authorities = new HashSet<>(oidcUser.getAuthorities());
        authorities.addAll(extractAuthorities(userRequest));

        if (oidcUser.getUserInfo() == null) {
            return new DefaultOidcUser(authorities, oidcUser.getIdToken());
        }

        return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
    }

    private Set<GrantedAuthority> extractAuthorities(OidcUserRequest userRequest) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        if (userRequest.getIdToken() != null && userRequest.getIdToken().getClaims() != null) {
            authorities.addAll(roleMapper.extractAuthorities(userRequest.getIdToken().getClaims()));
        }
        if (userRequest.getAccessToken() != null && userRequest.getAccessToken().getTokenValue() != null) {
            authorities.addAll(roleMapper.extractAuthoritiesFromJwt(userRequest.getAccessToken().getTokenValue()));
        }
        return authorities;
    }
}
