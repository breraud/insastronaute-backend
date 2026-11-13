package fr.insastronaute.webapi.auth.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.List;

@Component
public class KeycloakAdminGroupMembershipGateway
        implements KeycloakGroupMembershipGateway, KeycloakRealmRoleGateway, KeycloakAccountGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeycloakAdminGroupMembershipGateway.class);

    private static final int MAX_GROUPS = 1000;

    private final RestClient restClient;
    private final String tokenEndpoint;
    private final String adminRealmEndpoint;
    private final String clientId;
    private final String clientSecret;

    private volatile CachedToken cachedToken;

    /** Un role absent se signale une fois, pas a chaque rafraichissement. */
    private final java.util.Set<String> absentRolesAlreadyReported = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final java.util.concurrent.ConcurrentMap<String, RoleRepresentation> cachedRoles =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired
    public KeycloakAdminGroupMembershipGateway(
            @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri}") String issuerUri,
            @Value("${app.keycloak.admin.base-url:}") String adminBaseUrl,
            @Value("${spring.security.oauth2.client.registration.keycloak.client-id}") String clientId,
            @Value("${spring.security.oauth2.client.registration.keycloak.client-secret}") String clientSecret,
            KeycloakAdminProperties properties
    ) {
        this(createRestClient(properties), issuerUri, adminBaseUrl, clientId, clientSecret);
    }

    KeycloakAdminGroupMembershipGateway(
            RestClient restClient,
            String issuerUri,
            String clientId,
            String clientSecret
    ) {
        this(restClient, issuerUri, "", clientId, clientSecret);
    }

    /**
     * @param adminBaseUrl racine par ou joindre Keycloak pour l'administration, vide pour
     *                     la deduire de l'emetteur.
     *
     *                     <p>Ces appels sont de serveur a serveur : personne ne les regarde
     *                     passer, et rien n'oblige a sortir par le domaine public. En
     *                     production celui-ci est derriere Cloudflare, dont le defi
     *                     « managed » attend un navigateur : la demande de jeton recevait
     *                     une page HTML en 403, et l'annuaire du staff restait vide. Les
     *                     autres acces a Keycloak avaient deja leur porte interne
     *                     (`KEYCLOAK_JWK_SET_URI`) ; celui-ci n'en avait pas.</p>
     */
    KeycloakAdminGroupMembershipGateway(
            RestClient restClient,
            String issuerUri,
            String adminBaseUrl,
            String clientId,
            String clientSecret
    ) {
        this.restClient = restClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;

        String normalizedIssuer = stripTrailingSlash(issuerUri);
        URI issuer = URI.create(normalizedIssuer);
        String issuerPath = issuer.getPath();
        int realmMarker = issuerPath.lastIndexOf("/realms/");
        if (realmMarker < 0 || realmMarker + "/realms/".length() >= issuerPath.length()) {
            throw new IllegalArgumentException("Keycloak issuer URI must contain /realms/{realm}");
        }

        String realm = issuerPath.substring(realmMarker + "/realms/".length());
        if (realm.contains("/")) {
            throw new IllegalArgumentException("Keycloak issuer URI must identify one realm");
        }

        String serverBase = normalizedIssuer.substring(0, normalizedIssuer.length() - issuerPath.length())
                + issuerPath.substring(0, realmMarker);
        // Sans racine explicite, `serverBase + "/realms/" + realm` reconstitue exactement
        // l'emetteur : le chemin par defaut est donc inchange, au caractere pres.
        String base = adminBaseUrl == null || adminBaseUrl.isBlank()
                ? serverBase
                : stripTrailingSlash(adminBaseUrl);
        this.tokenEndpoint = base + "/realms/" + realm + "/protocol/openid-connect/token";
        this.adminRealmEndpoint = base + "/admin/realms/" + realm;
    }

    private volatile CachedRealmGroups cachedRealmGroups;

    /**
     * Les groupes du realm, relus au plus une fois par demi-heure.
     *
     * <p>L'echeance se mesure a l'heure reelle : voir {@link #accessToken()}.</p>
     */
    @Override
    public List<Group> listRealmGroups() {
        CachedRealmGroups currentCache = cachedRealmGroups;
        if (currentCache != null && currentCache.expiresAt().isAfter(Instant.now())) {
            return currentCache.groups();
        }

        synchronized (this) {
            currentCache = cachedRealmGroups;
            if (currentCache != null && currentCache.expiresAt().isAfter(Instant.now())) {
                return currentCache.groups();
            }

            GroupRepresentation[] groups = restClient.get()
                    .uri(adminRealmEndpoint + "/groups?briefRepresentation=true&max=" + MAX_GROUPS)
                    .headers(headers -> headers.setBearerAuth(accessToken()))
                    .retrieve()
                    .body(GroupRepresentation[].class);

            List<Group> flattenedGroups = new ArrayList<>();
            if (groups != null) {
                Arrays.stream(groups).forEach(group -> flatten(group, flattenedGroups));
            }
            List<Group> result = List.copyOf(flattenedGroups);
            cachedRealmGroups = new CachedRealmGroups(result, Instant.now().plusSeconds(1800));
            return result;
        }
    }

    /**
     * L'attribut {@code department} d'un compte.
     *
     * <p>Un compte illisible rend une liste vide plutot que de faire echouer l'appelant :
     * c'est a lui de decider si l'absence d'attribut vaut « aucun departement ».</p>
     */
    @Override
    public List<String> listUserDepartmentAttribute(String userId) {
        UserAttributesRepresentation user = restClient.get()
                .uri(adminRealmEndpoint + "/users/{userId}", userId)
                .headers(headers -> headers.setBearerAuth(accessToken()))
                .retrieve()
                .body(UserAttributesRepresentation.class);

        if (user == null || user.attributes() == null) {
            return List.of();
        }
        List<String> values = user.attributes().get("department");
        return values == null ? List.of() : values;
    }

    @Override
    public List<Group> listUserGroups(String userId) {
        GroupRepresentation[] groups = restClient.get()
                .uri(adminRealmEndpoint + "/users/{userId}/groups?briefRepresentation=true&max=" + MAX_GROUPS, userId)
                .headers(headers -> headers.setBearerAuth(accessToken()))
                .retrieve()
                .body(GroupRepresentation[].class);

        if (groups == null) {
            return List.of();
        }
        return Arrays.stream(groups).map(group -> new Group(group.id(), group.name())).toList();
    }

    @Override
    public void joinGroup(String userId, String groupId) {
        restClient.put()
                .uri(adminRealmEndpoint + "/users/{userId}/groups/{groupId}", userId, groupId)
                .headers(headers -> headers.setBearerAuth(accessToken()))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void leaveGroup(String userId, String groupId) {
        restClient.delete()
                .uri(adminRealmEndpoint + "/users/{userId}/groups/{groupId}", userId, groupId)
                .headers(headers -> headers.setBearerAuth(accessToken()))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public java.util.Set<String> listActiveKeycloakUserIds() {
        try {
            UserRepresentation[] users = restClient.get()
                    .uri(adminRealmEndpoint + "/users?briefRepresentation=true&max=5000")
                    .headers(headers -> headers.setBearerAuth(accessToken()))
                    .retrieve()
                    .body(UserRepresentation[].class);

            if (users == null) {
                return java.util.Set.of();
            }
            return java.util.Arrays.stream(users).map(UserRepresentation::id).collect(java.util.stream.Collectors.toSet());
        } catch (Exception exception) {
            return java.util.Set.of();
        }
    }

    @Override
    public boolean deleteAccount(String keycloakUserId) {
        try {
            restClient.delete()
                    .uri(adminRealmEndpoint + "/users/{userId}", keycloakUserId)
                    .headers(headers -> headers.setBearerAuth(accessToken()))
                    .retrieve()
                    .toBodilessEntity();
            return false;
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound absent) {
            // Deja parti : c'est l'etat vise. Echouer ici bloquerait une reprise apres un
            // echec survenu plus loin dans la suppression, et interdirait de nettoyer une
            // ligne orpheline dont le compte a ete efface ailleurs.
            LOGGER.info("Compte Keycloak {} deja absent lors de la suppression.", keycloakUserId);
            return true;
        }
    }

    @Override
    public void logoutUser(String keycloakUserId) {
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            return;
        }
        try {
            restClient.post()
                    .uri(adminRealmEndpoint + "/users/{userId}/logout", keycloakUserId)
                    .headers(headers -> headers.setBearerAuth(accessToken()))
                    .retrieve()
                    .toBodilessEntity();
            LOGGER.info("Sessions Keycloak deconnectees pour l'utilisateur {}", keycloakUserId);
        } catch (HttpClientErrorException.NotFound absent) {
            LOGGER.info("Compte Keycloak {} deja absent lors de la deconnexion.", keycloakUserId);
        } catch (Exception exception) {
            LOGGER.warn("Echec de la deconnexion Keycloak pour {} : {}", keycloakUserId, exception.getMessage());
        }
    }

    @Override
    public void assignRealmRole(String keycloakUserId, String roleName) {
        RoleRepresentation role = realmRole(roleName);
        restClient.post()
                .uri(adminRealmEndpoint + "/users/{userId}/role-mappings/realm", keycloakUserId)
                .headers(headers -> headers.setBearerAuth(accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void removeRealmRole(String keycloakUserId, String roleName) {
        RoleRepresentation role = realmRole(roleName);
        restClient.method(org.springframework.http.HttpMethod.DELETE)
                .uri(adminRealmEndpoint + "/users/{userId}/role-mappings/realm", keycloakUserId)
                .headers(headers -> headers.setBearerAuth(accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public List<String> listRealmRoleNames(String keycloakUserId) {
        RoleRepresentation[] roles = restClient.get()
                .uri(adminRealmEndpoint + "/users/{userId}/role-mappings/realm", keycloakUserId)
                .headers(headers -> headers.setBearerAuth(accessToken()))
                .retrieve()
                .body(RoleRepresentation[].class);

        if (roles == null) {
            return List.of();
        }
        return Arrays.stream(roles).map(RoleRepresentation::name).toList();
    }

    @Override
    public java.util.Optional<List<String>> listUsersInRealmRole(String roleName) {
        try {
            UserRepresentation[] users = restClient.get()
                    .uri(adminRealmEndpoint + "/roles/{roleName}/users?max=2000", roleName)
                    .headers(headers -> headers.setBearerAuth(accessToken()))
                    .retrieve()
                    .body(UserRepresentation[].class);

            if (users == null) {
                return java.util.Optional.of(List.of());
            }
            return java.util.Optional.of(
                    Arrays.stream(users).map(UserRepresentation::id).filter(Objects::nonNull).toList());
        } catch (HttpClientErrorException.NotFound absent) {
            // Un role que le realm ne declare pas n'a legitimement aucun membre : c'est une
            // reponse, pas un echec. On le dit une fois -- la configuration merite d'etre
            // corrigee -- et non toutes les cinq minutes.
            if (absentRolesAlreadyReported.add(roleName)) {
                LOGGER.warn("Le role [{}] n'existe pas dans ce realm : aucun compte ne le porte.", roleName);
            }
            return java.util.Optional.of(List.of());
        } catch (RestClientException exception) {
            // Ici on ne sait rien. Rendre une liste vide ferait passer l'echec pour une
            // reponse, et l'appelant en tirerait que le role n'a personne.
            LOGGER.warn("Membres du role [{}] illisibles : {}", roleName, exception.getMessage());
            return java.util.Optional.empty();
        }
    }

    /**
     * La definition d'un role ne change jamais en pratique ; la relire a chaque
     * validation ferait deux appels la ou un suffit.
     */
    private RoleRepresentation realmRole(String roleName) {
        return cachedRoles.computeIfAbsent(roleName, name -> {
            try {
                RoleRepresentation role = restClient.get()
                        .uri(adminRealmEndpoint + "/roles/{roleName}", name)
                        .headers(headers -> headers.setBearerAuth(accessToken()))
                        .retrieve()
                        .body(RoleRepresentation.class);
                if (role == null) {
                    throw new IllegalStateException("Le role de realm " + name + " est introuvable dans Keycloak.");
                }
                return role;
            } catch (org.springframework.web.client.RestClientResponseException exception) {
                int statusCode = exception.getStatusCode().value();
                if (statusCode == 404) {
                    throw new IllegalStateException(
                            "Le role de realm " + name + " est introuvable dans Keycloak (HTTP 404).",
                            exception
                    );
                }
                if (statusCode == 403) {
                    throw new IllegalStateException(
                            "Lecture du role de realm " + name + " refusee par Keycloak (HTTP 403) : "
                                    + "verifier les droits du compte de service (view-realm, manage-users).",
                            exception
                    );
                }
                throw new IllegalStateException(
                        "Echec de la lecture du role de realm " + name + " dans Keycloak (HTTP "
                                + statusCode + ").",
                        exception
                );
            }
        });
    }

    /**
     * Le jeton d'administration Keycloak, repris du cache tant qu'il vaut.
     *
     * <p>Les echeances de ce cache se mesurent a l'heure reelle, jamais a celle du jeu.
     * Elles decrivent la duree de vie d'un jeton emis par Keycloak, qui ignore tout de
     * nos voyages dans le temps : un membre du staff qui se projetait dans le futur
     * datait le cache de la-bas, la garde le trouvait valable pour toujours, et chaque
     * appel repartait avec un jeton reellement perime — 401, pour tout le monde, jusqu'au
     * redemarrage.</p>
     */
    private String accessToken() {
        CachedToken currentToken = cachedToken;
        if (currentToken != null && currentToken.expiresAt().isAfter(Instant.now().plusSeconds(10))) {
            return currentToken.value();
        }

        synchronized (this) {
            currentToken = cachedToken;
            if (currentToken != null && currentToken.expiresAt().isAfter(Instant.now().plusSeconds(10))) {
                return currentToken.value();
            }

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);

            TokenResponse response = restClient.post()
                    .uri(tokenEndpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new IllegalStateException("Keycloak token endpoint returned no access token");
            }

            long expiresIn = Math.max(1, response.expiresIn());
            cachedToken = new CachedToken(response.accessToken(), Instant.now().plusSeconds(expiresIn));
            return response.accessToken();
        }
    }

    private static void flatten(GroupRepresentation group, List<Group> destination) {
        destination.add(new Group(group.id(), group.name()));
        if (group.subGroups() != null) {
            group.subGroups().forEach(child -> flatten(child, destination));
        }
    }

    private static RestClient createRestClient(KeycloakAdminProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record CachedToken(String value, Instant expiresAt) {
    }

    private record CachedRealmGroups(List<Group> groups, Instant expiresAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GroupRepresentation(String id, String name, List<GroupRepresentation> subGroups) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UserAttributesRepresentation(java.util.Map<String, List<String>> attributes) {
    }

    private record UserRepresentation(String id, String username, String email) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RoleRepresentation(String id, String name) {
    }
}
