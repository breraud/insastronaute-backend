package fr.insastronaute.webapi.common.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Ajoute {@code kc_action} a la requete d'autorisation, quand elle le demande.
 *
 * <p>Keycloak sait executer une action ponctuelle - enregistrer une passkey, changer un
 * mot de passe - au milieu d'une authentification : c'est le parametre {@code kc_action}
 * sur le point d'autorisation. Mais la requete doit rester <strong>celle de Spring</strong>,
 * avec le {@code redirect_uri} declare sur le client.</p>
 *
 * <p>La fabriquer a la main paraissait plus direct et ne l'est pas : le client
 * {@code inte-backend} ne declare qu'une seule URI de retour, son propre callback.
 * N'importe quelle autre se fait refuser par Keycloak avec
 * <em>Invalid parameter: redirect_uri</em>, ce qui est exactement ce qu'il faut qu'il
 * fasse - une URI de retour librement choisie serait un redirecteur ouvert.</p>
 *
 * <p>Sans le parametre, ce resolveur ne touche a rien et rend la requete telle quelle :
 * le chemin de connexion ordinaire, qui est le seul que tout le monde emprunte, ne
 * change pas d'un octet.</p>
 */
public final class KcActionAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    public static final String PARAMETER = "kc_action";

    /**
     * Les actions qu'on accepte de declencher.
     *
     * <p>Une liste fermee, et pas le parametre tel quel : le laisser passer permettrait
     * a n'importe qui de forcer n'importe quelle action requise de Keycloak sur sa propre
     * session - dont {@code delete_account}.</p>
     */
    private static final Set<String> ALLOWED = Set.of(
            "webauthn-register-passwordless",
            "webauthn-register",
            "UPDATE_PASSWORD",
            "CONFIGURE_TOTP"
    );

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public KcActionAuthorizationRequestResolver(ClientRegistrationRepository clients, String baseUri) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(clients, baseUri);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return withKcAction(delegate.resolve(request), request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return withKcAction(delegate.resolve(request, clientRegistrationId), request);
    }

    private OAuth2AuthorizationRequest withKcAction(OAuth2AuthorizationRequest authorized, HttpServletRequest request) {
        if (authorized == null) {
            return null;
        }
        String action = request.getParameter(PARAMETER);
        if (action == null || !ALLOWED.contains(action)) {
            return authorized;
        }
        Map<String, Object> parameters = new LinkedHashMap<>(authorized.getAdditionalParameters());
        parameters.put(PARAMETER, action);
        return OAuth2AuthorizationRequest.from(authorized).additionalParameters(parameters).build();
    }
}
