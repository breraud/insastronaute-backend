package fr.insastronaute.webapi.auth.keycloak;

import java.util.List;
import java.util.Optional;

/**
 * Assignation directe des roles de realm.
 *
 * <p>Interface distincte de {@link KeycloakGroupMembershipGateway} bien que portee par
 * la meme classe : le cache de jeton et la resolution de l'endpoint admin y vivent
 * deja, et les dupliquer ailleurs couterait plus que d'elargir un peu celle-la. Les
 * consommateurs injectent l'interface dont ils ont besoin, pas la classe.</p>
 */
public interface KeycloakRealmRoleGateway {

    void assignRealmRole(String keycloakUserId, String roleName);

    void removeRealmRole(String keycloakUserId, String roleName);

    List<String> listRealmRoleNames(String keycloakUserId);

    /**
     * Les identifiants Keycloak des comptes portant ce role.
     *
     * <p>Le sens inverse de {@link #listRealmRoleNames}. Demander ses roles a chaque compte
     * d'une page ferait un appel par ligne ; demander les membres d'un role en fait un par
     * role, et les roles sont une douzaine quand les comptes sont deux mille.</p>
     *
     * <p>Rend {@code Optional.empty()} quand la lecture a echoue, et une liste -- vide au
     * besoin -- quand la reponse fait autorite. La nuance est le tout : un role absent du
     * realm n'a legitimement aucun membre, tandis qu'un Keycloak muet n'apprend rien. Rendre
     * une liste vide dans les deux cas laissait un echec passer pour une reponse, et le
     * staff pour des joueurs.</p>
     */
    Optional<List<String>> listUsersInRealmRole(String roleName);

    /**
     * Termine toutes les sessions actives du compte dans Keycloak.
     */
    void logoutUser(String keycloakUserId);
}

