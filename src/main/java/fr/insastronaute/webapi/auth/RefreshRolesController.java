package fr.insastronaute.webapi.auth;

import fr.insastronaute.webapi.auth.keycloak.KeycloakRealmRoleGateway;
import fr.insastronaute.webapi.common.security.roles.KeycloakRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Relit les roles du joueur chez Keycloak et les reinjecte dans sa session.
 *
 * <p>Assigner un role ne change pas un jeton deja emis. Sans cet endpoint, un joueur
 * valide resterait bloque jusqu'a ce qu'il se deconnecte et revienne, devant le respo
 * qui vient de lui dire que c'est bon.</p>
 *
 * <p>Il est volontairement couteux et volontairement rare : le front n'y touche qu'une
 * fois, quand /api/session lui apprend que la decision est tombee. Le faire interroger
 * en boucle ferait, avec quelques centaines de joueurs en attente, autant d'appels a
 * l'API admin de Keycloak toutes les trente secondes.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class RefreshRolesController {

    private static final Logger log = LoggerFactory.getLogger(RefreshRolesController.class);

    private final KeycloakRealmRoleGateway roleGateway;
    private final SecurityContextRepository securityContextRepository;

    // Cette configuration ne publie pas de bean SecurityContextRepository : Spring
    // Security n'en cree un que pour son propre usage interne au sein de la chaine de
    // filtres, sans le publier dans le contexte applicatif. Sans bean a injecter, on se
    // rabat sur celui que la chaine utilise elle-meme par defaut.
    public RefreshRolesController(KeycloakRealmRoleGateway roleGateway) {
        this.roleGateway = roleGateway;
        this.securityContextRepository = new HttpSessionSecurityContextRepository();
    }

    @PostMapping("/refresh-roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void refreshRoles(
            @AuthenticationPrincipal OidcUser oidcUser,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (oidcUser == null || current == null) {
            return;
        }

        Set<GrantedAuthority> authorities = new HashSet<>(current.getAuthorities());
        List<String> realmRoles;
        try {
            realmRoles = roleGateway.listRealmRoleNames(oidcUser.getSubject());
        } catch (RuntimeException e) {
            // On journalise et on relaie l'echec plutot que de le taire. Repondre 204
            // sur une lecture ratee ferait croire au front que le rafraichissement a
            // abouti : il cesserait de reessayer et laisserait le joueur devant son
            // ecran d'attente, sur un compte pourtant valide.
            log.warn("Relecture des roles Keycloak impossible pour {} : {}",
                    oidcUser.getSubject(), e.getMessage());
            throw e;
        }

        realmRoles.stream()
                .filter(KeycloakRoleMapper::isApplicationRole)
                .forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));

        // Remplacer l'authentification dans le contexte ET la persister : sans la
        // seconde etape, la session HTTP garde l'ancienne et le joueur reste bloque
        // des la requete suivante.
        Authentication refreshed = new OAuth2AuthenticationToken(oidcUser, authorities, "keycloak");
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(refreshed);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
