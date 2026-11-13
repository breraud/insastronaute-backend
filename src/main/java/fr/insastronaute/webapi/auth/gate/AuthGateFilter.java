package fr.insastronaute.webapi.auth.gate;

import fr.insastronaute.webapi.auth.gate.dto.GateEnterResponse;
import fr.insastronaute.webapi.common.security.config.AppSecurityProperties;
import fr.insastronaute.webapi.common.security.oauth.KcActionAuthorizationRequestResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class AuthGateFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthGateFilter.class);
    private static final String OAUTH_AUTHORIZATION_BASE = "/oauth2/authorization/";

    /**
     * Parametres que le portail doit rendre au bout du detour.
     *
     * <p>Le portail reconstruit l'URL d'autorisation de zero pour y poser son jeton : tout
     * ce que l'appelant avait mis dans la requete disparait a ce moment-la. {@code kc_action}
     * demande a Keycloak d'executer une action au passage - enregistrer une passkey, par
     * exemple. Perdu, il ne provoque aucune erreur : Keycloak voit une session SSO valide et
     * renvoie l'utilisateur aussitot, ce qui ressemble exactement a un bouton qui ne fait
     * rien. La panne est silencieuse des que le portail est actif, donc en production.</p>
     */
    private static final List<String> PRESERVED_PARAMETERS =
            List.of(KcActionAuthorizationRequestResolver.PARAMETER);

    private final AuthGateTokenService tokenService;
    private final AuthGateService authGateService;
    private final AuthGateProperties gateProperties;
    private final AppSecurityProperties securityProperties;

    public AuthGateFilter(
            AuthGateTokenService tokenService,
            AuthGateService authGateService,
            AuthGateProperties gateProperties,
            AppSecurityProperties securityProperties
    ) {
        this.tokenService = tokenService;
        this.authGateService = authGateService;
        this.gateProperties = gateProperties;
        this.securityProperties = securityProperties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String uri = request.getRequestURI();

        // Only filter OAuth authorization start endpoints (e.g. /oauth2/authorization/keycloak)
        if (!uri.startsWith(OAUTH_AUTHORIZATION_BASE) || !gateProperties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String gateToken = request.getParameter("gate_token");
        var validated = tokenService.validateToken(gateToken);

        if (validated.isPresent()) {
            filterChain.doFilter(request, response);
            return;
        }

        String redirectUri = request.getParameter("redirect_uri");

        // Intelligently check if capacity allows immediate on-the-fly admission
        GateEnterResponse enterResponse = authGateService.enter(null, redirectUri);
        if (enterResponse.status() == GateStatus.ADMITTED && enterResponse.redirectUrl() != null) {
            response.sendRedirect(withPreservedParameters(enterResponse.redirectUrl(), request));
            return;
        }

        LOGGER.info("Direct OAuth initiation queued via AuthGate: URI [{}] ticket [{}]", uri, enterResponse.ticketId());

        String waitingRoomUrl = waitingRoomFor(redirectUri) + "/file-d-attente";
        if (redirectUri != null && !redirectUri.isBlank()) {
            waitingRoomUrl += "?redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        }

        response.sendRedirect(withPreservedParameters(waitingRoomUrl, request));
    }

    /**
     * L'application ou renvoyer celui qu'on met en file.
     *
     * Le portail envoyait tout le monde sur la salle d'attente du front joueur, seule
     * valeur que {@code frontendBaseUrl} connaisse. Un membre du staff qui se reconnecte au
     * panneau d'administration se retrouvait donc dans l'application des joueurs, sur un
     * autre domaine -- au moment precis ou il avait quelque chose d'urgent a y faire.
     *
     * L'origine se lit dans le {@code redirect_uri} demande, mais celui-la vient du client :
     * le suivre sans le verifier ferait du portail une redirection ouverte. Il n'est donc
     * retenu que s'il figure parmi les origines autorisees, celles-la memes que la politique
     * CORS admet. Sinon on retombe sur le front joueur, qui reste le cas de tres loin le
     * plus frequent.
     */
    private String waitingRoomFor(String redirectUri) {
        String fallback = removeTrailingSlash(securityProperties.getFrontendBaseUrl());
        if (redirectUri == null || redirectUri.isBlank()) {
            return fallback;
        }
        try {
            URI target = URI.create(redirectUri);
            if (target.getScheme() == null || target.getHost() == null) {
                return fallback;
            }
            String origin = target.getScheme() + "://" + target.getHost()
                    + (target.getPort() == -1 ? "" : ":" + target.getPort());
            for (String allowed : securityProperties.getAllowedOrigins()) {
                if (origin.equalsIgnoreCase(removeTrailingSlash(allowed))) {
                    return origin;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Une adresse que l'on ne sait pas lire n'est pas une origine : on retombe.
        }
        return fallback;
    }

    private static String removeTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /**
     * Recopie sur {@code url} les parametres de la requete d'origine que le portail a perdus
     * en reconstruisant l'adresse. Un parametre absent ou vide n'est pas recopie.
     */
    private static String withPreservedParameters(String url, HttpServletRequest request) {
        StringBuilder builder = new StringBuilder(url);
        for (String name : PRESERVED_PARAMETERS) {
            String value = request.getParameter(name);
            if (value == null || value.isBlank()) {
                continue;
            }
            builder.append(builder.indexOf("?") >= 0 ? '&' : '?')
                    .append(name)
                    .append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
        return builder.toString();
    }
}
