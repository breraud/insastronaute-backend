package fr.insastronaute.webapi.auth.sessionreset;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.web.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Retire les sessions de staff qu'un administrateur a declarees perimees.
 *
 * <p>Les autorites sont extraites du jeton a la connexion et n'y reviennent plus. Retirer
 * un role dans Keycloak ne change donc rien pour qui est deja connecte, et le
 * rafraichissement existant ({@code /api/auth/refresh-roles}) n'aide pas : il n'ajoute que
 * ce que Keycloak lui apprend, sans jamais rien retirer, et ne touche que la session de
 * son appelant.</p>
 *
 * <p>La session est fermee plutot que relue. Une relecture passerait par l'API admin de
 * Keycloak, qui ne rend que les roles de realm : les autorites venues d'un role client
 * disparaitraient sans bruit. La reconnexion, elle, refait passer les roles par le jeton,
 * d'ou ils venaient - retraits compris, et sans dependre de ce que la console contient.</p>
 *
 * <p>Seul le staff est concerne. Un joueur ordinaire n'a rien a reattribuer, et le faire
 * repasser par Keycloak enverrait deux mille reconnexions d'un coup sur le portail
 * d'authentification.</p>
 */
public class StaffSessionResetFilter extends OncePerRequestFilter {

    static final String SESSION_ATTRIBUTE = "insastronaute.staffSessionEpoch";

    private final StaffSessionResetService resetService;
    private final ObjectMapper objectMapper;
    private final Set<String> staffAuthorities;

    public StaffSessionResetFilter(
            StaffSessionResetService resetService,
            ObjectMapper objectMapper,
            Set<String> staffRoles
    ) {
        this.resetService = resetService;
        this.objectMapper = objectMapper;
        this.staffAuthorities = staffRoles.stream()
                .map(role -> "ROLE_" + role)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!holdsStaffAuthority(authentication)) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long current = resetService.currentEpoch();
        Object stamp = session.getAttribute(SESSION_ATTRIBUTE);

        // Une session qui n'a jamais vu le compteur prend celui du moment. Sans cette
        // reserve, la premiere mise en service deconnecterait tout le staff d'un coup,
        // pour une reinitialisation que personne n'a demandee.
        if (!(stamp instanceof Long seen)) {
            session.setAttribute(SESSION_ATTRIBUTE, current);
            filterChain.doFilter(request, response);
            return;
        }

        if (seen >= current) {
            filterChain.doFilter(request, response);
            return;
        }

        session.invalidate();
        SecurityContextHolder.clearContext();
        writeExpired(request, response);
    }

    private boolean holdsStaffAuthority(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(staffAuthorities::contains);
    }

    /**
     * Un 401 plutot qu'un 403 : le front le traite deja en repartant vers Keycloak, ou une
     * session SSO encore valide renvoie la personne sans rien lui redemander.
     */
    private void writeExpired(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiErrorResponse body = new ApiErrorResponse(
                TimeTravelContext.offsetDateTimeNow(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Les roles ont ete reattribues, reconnecte-toi.",
                request.getRequestURI(),
                List.of(),
                "ROLES_RESET"
        );

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
