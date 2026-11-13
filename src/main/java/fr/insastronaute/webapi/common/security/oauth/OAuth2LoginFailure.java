package fr.insastronaute.webapi.common.security.oauth;

import java.util.Set;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

/**
 * Pourquoi une connexion s'est arretee, en un mot que le front sait expliquer.
 *
 * <p>Toutes les interruptions ne sont pas des pannes. Un joueur qui refuse les conditions
 * a pris une decision ; quelqu'un qui revient en arriere dans son navigateur aussi. Les
 * traiter comme des erreurs serveur produit une trace de cent lignes par clic et renvoie
 * le joueur avec un code qui ne lui apprend rien -- le meme que si Keycloak etait tombe.</p>
 */
public final class OAuth2LoginFailure {

    /** Ce que Keycloak repond quand la page des conditions a ete refusee. */
    private static final String TERMS_DECLINED_DESCRIPTION = "termsAndConditionsDeclined";

    /** Ce que Keycloak repond quand le code d'autorisation n'est plus echangeable. */
    private static final String INVALID_GRANT = "invalid_grant";

    public static final String TERMS_DECLINED = "terms_declined";
    public static final String SESSION_EXPIRED = "authorization_request_not_found";
    /**
     * Le code d'autorisation ne vaut plus rien : deja echange, ou perime.
     *
     * <p>Il est a usage unique et vit une minute. Recharger la page de retour, revenir
     * en arriere dessus, ou restaurer l'onglet le lendemain le rejoue tel quel -- et
     * Keycloak refuse, a juste titre. C'est un geste de navigation, pas une panne, et
     * se reconnecter suffit.</p>
     */
    public static final String LOGIN_CODE_EXPIRED = "login_code_expired";
    public static final String GENERIC = "oauth2_failure";

    /** Les arrets qui relevent d'un geste de l'utilisateur, pas d'une panne. */
    private static final Set<String> EXPECTED = Set.of(TERMS_DECLINED, SESSION_EXPIRED, LOGIN_CODE_EXPIRED);

    private OAuth2LoginFailure() {
    }

    /**
     * Le motif de l'echec, tel qu'il partira dans l'URL de retour.
     *
     * <p>Le refus des conditions se reconnait a la description et non au code : Keycloak
     * repond {@code access_denied}, qui couvre aussi bien d'autres refus.</p>
     */
    public static String reasonOf(AuthenticationException exception) {
        if (!(exception instanceof OAuth2AuthenticationException oauthException)) {
            return GENERIC;
        }
        String code = oauthException.getError().getErrorCode();
        String description = oauthException.getError().getDescription();
        if (TERMS_DECLINED_DESCRIPTION.equals(description)) {
            return TERMS_DECLINED;
        }
        if (SESSION_EXPIRED.equals(code)) {
            return SESSION_EXPIRED;
        }
        // A l'echange du code, invalid_grant ne dit qu'une chose : ce code-la ne vaut
        // plus. Un reglage errone, lui, echouerait a chaque connexion et se verrait
        // au nombre, pas au niveau de trace.
        if (INVALID_GRANT.equals(code)) {
            return LOGIN_CODE_EXPIRED;
        }
        return GENERIC;
    }

    /** Vrai quand l'arret est attendu : a journaliser sans trace, comme un fait. */
    public static boolean isExpected(String reason) {
        return EXPECTED.contains(reason);
    }
}
