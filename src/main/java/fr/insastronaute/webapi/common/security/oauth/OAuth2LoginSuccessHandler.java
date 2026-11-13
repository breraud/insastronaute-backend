package fr.insastronaute.webapi.common.security.oauth;

import fr.insastronaute.webapi.common.security.config.AppSecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

public final class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
    private final RequestCache requestCache = new HttpSessionRequestCache();
    private static final java.util.Set<String> ACTION_STATUSES =
            java.util.Set.of("success", "cancel", "error");
    private final String defaultRedirectUri;
    private final java.time.Duration staffSessionTimeout;
    private final java.time.Duration playerSessionTimeout;
    private final java.util.Set<String> staffAuthorities;

    /**
     * @param staffRoles les roles, sans prefixe {@code ROLE_}, qui font une session courte.
     *                   Passes plutot que relus ici : la liste vit dans la configuration de
     *                   securite, ou elle sert deja aux regles d'acces, et en garder deux
     *                   copies revient a en avoir une fausse le jour ou l'une bouge.
     */
    public OAuth2LoginSuccessHandler(AppSecurityProperties securityProperties, java.util.Set<String> staffRoles) {
        this.defaultRedirectUri = new FrontendRedirectUriResolver(
                securityProperties.getFrontendBaseUrl(),
                securityProperties.getAllowedOrigins()
        ).defaultRedirectUri();
        this.staffSessionTimeout = securityProperties.getStaffSessionTimeout();
        this.playerSessionTimeout = securityProperties.getPlayerSessionTimeout();
        this.staffAuthorities = staffRoles.stream()
                .map(role -> "ROLE_" + role)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public void onAuthenticationSuccess(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Authentication authentication
    ) throws IOException {
        applySessionTimeout(request, authentication);
        redirectStrategy.sendRedirect(request, response,
                withActionStatus(resolveRedirectUri(request, response), request));
    }

    /**
     * Donne a la session la duree que le compte merite.
     *
     * <p>Un compte de staff ouvre des portes qu'un compte de joueur n'ouvre pas : la duree
     * pendant laquelle un telephone pose sur une table reste ce compte-la doit suivre ce
     * qu'il permet, pas le confort de son porteur. A l'inverse, reconnecter deux mille
     * etudiants chaque matin, c'est deux mille passages par Keycloak au moment ou tout le
     * monde arrive -- exactement l'afflux que le portail d'attente existe pour lisser.</p>
     *
     * <p>Pose ici, et une seule fois : la session vient d'etre creee et les roles viennent
     * d'etre lus. Le refaire a chaque requete ecrirait dans Redis pour une valeur qui ne
     * change pas.</p>
     *
     * <p>Le cookie, lui, reste regle sur la plus longue des deux durees : il ne porte qu'un
     * identifiant, et un identifiant que le serveur a oublie n'authentifie rien. Le limiter
     * a huit heures pour tout le monde rendrait au contraire les vingt-quatre heures des
     * joueurs inaccessibles.</p>
     */
    private void applySessionTimeout(HttpServletRequest request, Authentication authentication) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        boolean staff = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(staffAuthorities::contains);
        session.setMaxInactiveInterval(
                (int) (staff ? staffSessionTimeout : playerSessionTimeout).toSeconds());
    }

    /**
     * Transmet au front l'issue d'une action initiee par l'application.
     *
     * <p>Keycloak pose {@code kc_action_status} - {@code success}, {@code cancel} ou
     * {@code error} - sur l'URI de retour qu'il a recue, c'est-a-dire notre callback.
     * Sans ce relais, le front ne saurait jamais si l'enregistrement a abouti ou si la
     * personne a annule, et on marquerait les deux de la meme facon.</p>
     *
     * <p>La valeur est verifiee avant d'etre recopiee : elle finit dans une URL, et on
     * ne recopie pas dans une URL une chaine qu'un tiers a choisie.</p>
     */
    private static String withActionStatus(String redirectUri, HttpServletRequest request) {
        String status = request.getParameter("kc_action_status");
        if (status == null || !ACTION_STATUSES.contains(status)) {
            return redirectUri;
        }
        return redirectUri + (redirectUri.contains("?") ? "&" : "?") + "kc_action_status=" + status;
    }

    private String resolveRedirectUri(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);

        return resolveSessionRedirectUri(session)
                .orElseGet(() -> resolveDocumentationRedirectUri(request, response).orElse(defaultRedirectUri));
    }

    private Optional<String> resolveSessionRedirectUri(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }

        Object redirectUri = session.getAttribute(OAuth2LoginRedirectUriCaptureFilter.SESSION_ATTRIBUTE);
        session.removeAttribute(OAuth2LoginRedirectUriCaptureFilter.SESSION_ATTRIBUTE);

        if (redirectUri instanceof String value && !value.isBlank()) {
            return Optional.of(value);
        }

        return Optional.empty();
    }

    private Optional<String> resolveDocumentationRedirectUri(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        SavedRequest savedRequest = requestCache.getRequest(request, response);

        if (savedRequest == null || !isDocumentationPath(savedRequest.getRedirectUrl())) {
            return Optional.empty();
        }

        requestCache.removeRequest(request, response);

        return Optional.of(removeContinueMarker(savedRequest.getRedirectUrl()));
    }

    private static boolean isDocumentationPath(String redirectUrl) {
        try {
            String path = URI.create(redirectUrl).getPath();

            return "/swagger-ui.html".equals(path)
                    || path.startsWith("/swagger-ui/")
                    || "/v3/api-docs".equals(path)
                    || path.startsWith("/v3/api-docs/");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String removeContinueMarker(String redirectUrl) {
        if (redirectUrl.endsWith("?continue")) {
            return redirectUrl.substring(0, redirectUrl.length() - "?continue".length());
        }

        return redirectUrl;
    }
}
