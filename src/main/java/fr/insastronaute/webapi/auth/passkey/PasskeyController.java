package fr.insastronaute.webapi.auth.passkey;

import fr.insastronaute.webapi.common.security.oauth.KcActionAuthorizationRequestResolver;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

/**
 * La suggestion d'enregistrer une passkey.
 *
 * <p>L'enregistrement lui-meme appartient a Keycloak : on l'atteint par une action
 * initiee par l'application - {@code kc_action=webauthn-register-passwordless} sur le
 * point d'autorisation. Ce n'est donc pas une fenetre qui s'ouvre dans la page mais une
 * <strong>redirection complete</strong> : le joueur part, enregistre, et revient.</p>
 *
 * <p>Ce que ce controleur suit n'est pas « ce compte a une passkey » - Keycloak seul le
 * sait - mais « on lui a deja pose la question ». La distinction rend tout le reste
 * simple : aucun appel a Keycloak, aucun cache a invalider, et une valeur fausse ne
 * coute qu'une suggestion affichee en trop.</p>
 */
@RestController
@RequestMapping("/api/account/passkey")
@Tag(name = "Passkey")
public class PasskeyController {

    private static final String ACTION = "webauthn-register-passwordless";
    private static final Duration SNOOZE = Duration.ofDays(1);

    private final UserRepository users;

    public PasskeyController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/register")
    @Operation(
            summary = "Send the player to Keycloak to register a passkey",
            description = "A full OIDC redirect carrying kc_action. Keycloak shows its own screen and sends the "
                    + "player back with kc_action_status."
    )
    public ResponseEntity<Void> register(@RequestParam(required = false) String returnTo) {
        // On passe par le point d'autorisation de Spring, pas par celui de Keycloak
        // directement : le client ne declare qu'une URI de retour, son propre callback,
        // et toute autre se fait refuser -- « Invalid parameter: redirect_uri ». Le
        // parametre redirect_uri lu ici est un chemin du front, capte en session par
        // OAuth2LoginRedirectUriCaptureFilter et rejoue apres le retour.
        URI target = UriComponentsBuilder.fromUriString("/oauth2/authorization/keycloak")
                .queryParam(KcActionAuthorizationRequestResolver.PARAMETER, ACTION)
                .queryParam("redirect_uri", safeReturnTo(returnTo))
                .build()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(target).build();
    }

    /**
     * Ce que le retour de Keycloak apprend.
     *
     * <p>Seul {@code success} ferme la question. Sans cette lecture, on marquerait comme
     * traites ceux qui ont annule, et on ne le leur reproposerait jamais - alors que
     * c'est justement a eux qu'il faut reposer la question.</p>
     */
    @PostMapping("/answered")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Record the outcome of a registration attempt")
    @Transactional
    public void answered(
            @AuthenticationPrincipal OidcUser oidcUser,
            @RequestParam(name = "status", required = false) String status
    ) {
        AppUser user = currentUser(oidcUser);
        if ("success".equalsIgnoreCase(status)) {
            user.setPasskeyPromptAnsweredAt(Instant.now());
        } else {
            // Annulation ou erreur : on ne ferme pas la question, on la repousse.
            user.setPasskeyPromptSnoozedUntil(Instant.now().plus(SNOOZE));
        }
        users.save(user);
    }

    @PostMapping("/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Never suggest it again", description = "The settings entry stays available regardless.")
    @Transactional
    public void dismiss(@AuthenticationPrincipal OidcUser oidcUser) {
        AppUser user = currentUser(oidcUser);
        user.setPasskeyPromptAnsweredAt(Instant.now());
        users.save(user);
    }

    @PostMapping("/snooze")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Ask again tomorrow")
    @Transactional
    public void snooze(@AuthenticationPrincipal OidcUser oidcUser) {
        AppUser user = currentUser(oidcUser);
        user.setPasskeyPromptSnoozedUntil(Instant.now().plus(SNOOZE));
        users.save(user);
    }

    /**
     * Le retour ne peut pas etre choisi par l'appelant.
     *
     * <p>Un {@code redirect_uri} librement fourni est un redirecteur ouvert : on renvoie
     * toujours vers le front configure, et le chemin demande n'est retenu que s'il est
     * relatif.</p>
     */
    private String safeReturnTo(String returnTo) {
        if (returnTo == null || returnTo.isBlank() || !returnTo.startsWith("/") || returnTo.startsWith("//")) {
            return "/parametres";
        }
        return returnTo;
    }

    private AppUser currentUser(OidcUser oidcUser) {
        return users.findByKeycloakSub(oidcUser.getSubject())
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
    }
}
