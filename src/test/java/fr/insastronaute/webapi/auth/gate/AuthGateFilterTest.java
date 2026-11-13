package fr.insastronaute.webapi.auth.gate;

import fr.insastronaute.webapi.auth.gate.dto.GateEnterResponse;
import fr.insastronaute.webapi.common.security.config.AppSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Le portail d'attente reconstruit l'adresse d'autorisation pour y poser son jeton, et
 * perdait au passage tout ce que l'appelant y avait mis.
 *
 * <p>{@code kc_action} demande a Keycloak d'executer une action pendant la connexion -
 * enregistrer une passkey. Perdu, il ne declenche aucune erreur : Keycloak voit une session
 * valide et renvoie l'utilisateur immediatement. Le bouton semblait charger puis ne rien
 * faire, et seulement en production, ou le portail est actif.</p>
 */
class AuthGateFilterTest {

    private final AuthGateTokenService tokenService = mock(AuthGateTokenService.class);
    private final AuthGateService authGateService = mock(AuthGateService.class);
    private final AuthGateProperties gateProperties = new AuthGateProperties();
    private final AppSecurityProperties securityProperties = new AppSecurityProperties();

    private final AuthGateFilter filter =
            new AuthGateFilter(tokenService, authGateService, gateProperties, securityProperties);

    private MockHttpServletResponse redirectFor(GateEnterResponse enterResponse, String queryParameter, String value)
            throws Exception {
        when(tokenService.validateToken(any())).thenReturn(Optional.empty());
        when(authGateService.enter(any(), any())).thenReturn(enterResponse);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/keycloak");
        if (queryParameter != null) {
            request.setParameter(queryParameter, value);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static GateEnterResponse admitted() {
        return new GateEnterResponse(
                GateStatus.ADMITTED, "ticket", "jeton",
                "/oauth2/authorization/keycloak?gate_token=jeton", null, null, null);
    }

    private static GateEnterResponse queued() {
        return new GateEnterResponse(GateStatus.QUEUED, "ticket", null, null, 12, 40L, 6L);
    }

    @Test
    void admittingImmediatelyKeepsTheActionKeycloakWasAskedToRun() throws Exception {
        MockHttpServletResponse response =
                redirectFor(admitted(), "kc_action", "webauthn-register-passwordless");

        assertThat(response.getRedirectedUrl())
                .contains("gate_token=jeton")
                .contains("kc_action=webauthn-register-passwordless");
    }

    @Test
    void sendingToTheWaitingRoomKeepsTheActionToo() throws Exception {
        securityProperties.setFrontendBaseUrl("https://insastronaute.fr");

        MockHttpServletResponse response =
                redirectFor(queued(), "kc_action", "webauthn-register-passwordless");

        assertThat(response.getRedirectedUrl())
                .startsWith("https://insastronaute.fr/file-d-attente")
                .contains("kc_action=webauthn-register-passwordless");
    }

    /**
     * Sans action demandee, l'adresse ne doit pas gagner de parametre vide : une connexion
     * ordinaire reste exactement ce qu'elle etait.
     */
    @Test
    void anOrdinaryLoginGainsNothing() throws Exception {
        MockHttpServletResponse response = redirectFor(admitted(), null, null);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("/oauth2/authorization/keycloak?gate_token=jeton");
    }

    /**
     * Une requete qui porte deja un jeton valide traverse le filtre sans detour : rien
     * n'est reconstruit, donc rien n'est a preserver.
     */
    @Test
    void aRequestAlreadyHoldingATokenIsLeftAlone() throws Exception {
        when(tokenService.validateToken(anyString())).thenReturn(Optional.of(
                new AuthGateTokenService.ValidatedGateToken("ticket", null, Instant.now().plusSeconds(60))));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/keycloak");
        request.setParameter("gate_token", "jeton");
        request.setParameter("kc_action", "webauthn-register-passwordless");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getRedirectedUrl()).isNull();
    }

    /**
     * Le portail renvoyait tout le monde sur la salle d'attente du front joueur, seule
     * valeur que `frontendBaseUrl` connaisse. Un membre du staff qui se reconnecte au
     * panneau se retrouvait donc dans l'application des joueurs, sur un autre domaine --
     * au moment precis ou il avait quelque chose d'urgent a y faire.
     */
    @Test
    void queuingSendsTheStaffBackToTheApplicationTheyCameFrom() throws Exception {
        securityProperties.setFrontendBaseUrl("https://app.insastronaute.fr");
        securityProperties.setDomain("insastronaute.fr");
        when(tokenService.validateToken(any())).thenReturn(Optional.empty());
        when(authGateService.enter(any(), any())).thenReturn(queued());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/keycloak");
        request.setParameter("redirect_uri", "https://admin.insastronaute.fr/");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getRedirectedUrl()).startsWith("https://admin.insastronaute.fr/file-d-attente");
    }

    /**
     * Suivre le `redirect_uri` sans le verifier ferait du portail une redirection ouverte :
     * il vient du client. Une origine inconnue retombe donc sur le front joueur.
     */
    @Test
    void anUnknownOriginFallsBackInsteadOfBeingFollowed() throws Exception {
        securityProperties.setFrontendBaseUrl("https://app.insastronaute.fr");
        securityProperties.setDomain("insastronaute.fr");
        when(tokenService.validateToken(any())).thenReturn(Optional.empty());
        when(authGateService.enter(any(), any())).thenReturn(queued());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/keycloak");
        request.setParameter("redirect_uri", "https://ailleurs.example/piege");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getRedirectedUrl()).startsWith("https://app.insastronaute.fr/file-d-attente");
    }
}
