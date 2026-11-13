package fr.insastronaute.webapi.medialibrary.web;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

final class Authorities {

    private Authorities() {
    }

    static Set<String> of(OidcUser user) {
        if (user == null) {
            return Set.of();
        }
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }
}
