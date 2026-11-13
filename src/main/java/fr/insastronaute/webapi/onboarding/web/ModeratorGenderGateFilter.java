package fr.insastronaute.webapi.onboarding.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.web.ApiErrorResponse;
import fr.insastronaute.webapi.onboarding.service.ModeratorGenderGateService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Ferme la moderation a qui n'a pas declare son genre.
 *
 * <p>Ce champ decide de ce qu'on voit : sur un defi marque sensible, un moderateur qui n'est
 * pas administrateur ne traite que les soumissions de son propre genre. Sans choix, la colonne
 * vaut NEUTRAL par defaut -- une valeur que personne n'a posee et qui pourtant trie la file :
 * des preuves s'ouvrent, d'autres disparaissent, et le moderateur ne sait meme pas au nom de
 * quoi. Le garde transforme ce defaut silencieux en une question posee une fois.</p>
 *
 * <p>Il s'applique a tout le monde, administrateurs compris. Eux ne subissent pas la regle du
 * genre -- ils voient tout --, mais ils moderent les memes preuves que les autres, et la
 * declaration vaut pour l'equipe entiere : une exception pour ADMIN ferait de la question une
 * formalite reservee aux subalternes.</p>
 *
 * <p>Le garde ne barre que {@code /api/moderation}. Le PanelAdmin, lui, pose la question a
 * l'entree : c'est la ceinture, ceci est la bretelle -- ce qui est verrouille ici est
 * exactement ce que le genre gouverne.</p>
 */
public class ModeratorGenderGateFilter extends OncePerRequestFilter {

    private static final String MODERATION_PREFIX = "/api/moderation";

    private final ObjectMapper objectMapper;
    private final ModeratorGenderGateService gateService;

    public ModeratorGenderGateFilter(ObjectMapper objectMapper, ModeratorGenderGateService gateService) {
        this.objectMapper = objectMapper;
        this.gateService = gateService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals(MODERATION_PREFIX) || path.startsWith(MODERATION_PREFIX + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String subject = authenticatedSubject();

        // Personne d'identifiable : l'autorisation a deja tranche cette requete, et ce garde
        // n'a pas a doubler son verdict avec un message qui parlerait d'autre chose.
        if (subject == null || gateService.hasChosenGender(subject)) {
            chain.doFilter(request, response);
            return;
        }

        writeForbidden(request, response);
    }

    private String authenticatedSubject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            return oidcUser.getSubject();
        }

        return authentication.getName();
    }

    private void writeForbidden(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiErrorResponse body = new ApiErrorResponse(
                TimeTravelContext.offsetDateTimeNow(),
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "Renseigne ton genre avant de moderer : il decide des preuves sensibles que tu peux ouvrir.",
                request.getRequestURI(),
                List.of(),
                "MODERATOR_GENDER_REQUIRED"
        );

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
