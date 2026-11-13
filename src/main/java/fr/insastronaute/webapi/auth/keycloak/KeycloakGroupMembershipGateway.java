package fr.insastronaute.webapi.auth.keycloak;

import java.util.List;
import java.util.Set;

public interface KeycloakGroupMembershipGateway {

    List<Group> listRealmGroups();

    List<Group> listUserGroups(String userId);

    /**
     * L'attribut {@code department} d'un compte, tel que Keycloak le porte.
     *
     * <p>C'est cette valeur que la connexion lit en premier -- pas l'appartenance aux
     * groupes. Les deux peuvent diverger : la synchronisation qui pousse l'attribut vers
     * les groupes peut etre desactivee, echouer, ou viser un groupe qui n'existe pas.</p>
     *
     * @return les valeurs de l'attribut, vide s'il n'en porte pas
     */
    List<String> listUserDepartmentAttribute(String userId);

    void joinGroup(String userId, String groupId);

    void leaveGroup(String userId, String groupId);

    Set<String> listActiveKeycloakUserIds();

    record Group(String id, String name) {
    }
}
