package fr.insastronaute.webapi.common.security.roles;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class KeycloakRoleMapper {

    private static final Set<String> APP_ROLES = Set.of(
            "STUDENT",
            "STUDENT_LIMITED",
            "ACCES_SITE_ADMIN",
            "ADMIN",
            "RESPOS_DEFIS",
            "RESPOS_EVENT",
            "RESPO_CARTES",
            "RESPO_DEPART",
            "RESPO_GOODIES",
            "RESPO_TOURNOIS",
            "SOUS_RESPO_CARTES",
            "SOUS_RESPO_DEFIS",
            "SOUS_RESPO_DEPART",
            "SOUS_RESPO_EVENT",
            "SOUS_RESPO_GOODIES",
            "SOUS_RESPO_TOURNOIS"
    );

    private static final TypeReference<Map<String, Object>> CLAIMS_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final String clientId;

    public KeycloakRoleMapper(String clientId) {
        this(new ObjectMapper(), clientId);
    }

    public KeycloakRoleMapper(ObjectMapper objectMapper, String clientId) {
        this.objectMapper = objectMapper;
        this.clientId = clientId;
    }

    /**
     * Dit si un nom de role appartient a cette application.
     *
     * <p>Keycloak renvoie aussi ses propres roles internes ({@code offline_access},
     * {@code uma_authorization}), qui n'ont rien a faire dans les autorites de
     * l'application.</p>
     */
    public static boolean isApplicationRole(String roleName) {
        return APP_ROLES.contains(roleName);
    }

    public Set<GrantedAuthority> extractAuthorities(Map<String, Object> claims) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        extractRealmRoles(claims).forEach(role ->
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role))
        );

        extractClientRoles(claims).forEach(role ->
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role))
        );

        return authorities;
    }

    public Set<GrantedAuthority> extractAuthoritiesFromJwt(String jwt) {
        return decodeJwtClaims(jwt)
                .map(this::extractAuthorities)
                .orElseGet(Set::of);
    }

    private java.util.Optional<Map<String, Object>> decodeJwtClaims(String jwt) {
        String[] parts = jwt.split("\\.");

        if (parts.length < 2) {
            return java.util.Optional.empty();
        }

        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> claims = objectMapper.readValue(
                    new String(payload, StandardCharsets.UTF_8),
                    CLAIMS_TYPE
            );

            return java.util.Optional.of(claims);
        } catch (IllegalArgumentException | IOException exception) {
            return java.util.Optional.empty();
        }
    }

    private static Set<String> extractRealmRoles(Map<String, Object> claims) {
        Object realmAccess = claims.get("realm_access");

        if (!(realmAccess instanceof Map<?, ?> realmAccessMap)) {
            return Set.of();
        }

        Object roles = realmAccessMap.get("roles");

        return extractAppRoles(roles);
    }

    private Set<String> extractClientRoles(Map<String, Object> claims) {
        Object resourceAccess = claims.get("resource_access");

        if (!(resourceAccess instanceof Map<?, ?> resourceAccessMap)) {
            return Set.of();
        }

        Object clientAccess = resourceAccessMap.get(clientId);

        if (!(clientAccess instanceof Map<?, ?> clientAccessMap)) {
            return Set.of();
        }

        Object roles = clientAccessMap.get("roles");

        return extractAppRoles(roles);
    }

    private static Set<String> extractAppRoles(Object roles) {
        if (!(roles instanceof Collection<?> roleCollection)) {
            return Set.of();
        }

        Set<String> result = new HashSet<>();

        for (Object role : roleCollection) {
            // On passe par isApplicationRole plutot que de relire APP_ROLES ici : le
            // filtre de RefreshRolesController s'appuie sur cette methode, et deux
            // lectures separees de la meme liste finiraient par diverger - un role
            // ajoute d'un cote serait accorde a la connexion mais pas au
            // rafraichissement, ou l'inverse.
            if (role instanceof String roleName && isApplicationRole(roleName)) {
                result.add(roleName);
            }
        }

        return result;
    }
}
