package fr.insastronaute.webapi.auth.keycloak;

/**
 * Suppression d'un compte d'authentification.
 *
 * <p>Interface a part, et volontairement minuscule : c'est le seul geste irreversible du
 * cote Keycloak, et les services qui en ont besoin n'ont rien a faire des roles ni des
 * groupes. Ce qu'un service peut injecter borne ce qu'il peut casser.</p>
 */
public interface KeycloakAccountGateway {

    /**
     * Efface le compte, definitivement.
     *
     * <p>Un compte deja absent n'est pas une erreur : l'operation doit pouvoir etre
     * relancee apres un echec partiel sans se bloquer sur ce qu'elle a deja fait. C'est
     * aussi le cas d'une ligne orpheline, dont le compte a ete efface ailleurs.</p>
     *
     * @return vrai si le compte n'existait deja plus.
     */
    boolean deleteAccount(String keycloakUserId);

    /**
     * Termine toutes les sessions actives du compte dans Keycloak.
     */
    void logoutUser(String keycloakUserId);
}
