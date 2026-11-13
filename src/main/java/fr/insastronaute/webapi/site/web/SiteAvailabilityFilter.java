package fr.insastronaute.webapi.site.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.web.ApiErrorResponse;
import fr.insastronaute.webapi.site.service.SiteAvailabilityService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
 * Ferme l'API aux joueurs quand le site est desactive.
 *
 * <p>Il est enregistre apres {@code AuthorizationFilter} : l'authentification est
 * resolue, les autorites sont dans le contexte, et seules les requetes deja
 * autorisees arrivent ici.</p>
 *
 * <p>La liste blanche de connexion n'est pas negociable : sans elle, un membre du
 * staff deconnecte ne franchirait plus la porte d'entree pendant la fermeture, et
 * personne ne pourrait rouvrir le site.</p>
 */
public class SiteAvailabilityFilter extends OncePerRequestFilter {

    private static final String DEFAULT_MESSAGE = "Le site est momentanement ferme.";
    private static final Set<String> ALWAYS_OPEN = Set.of("/api/session", "/api/csrf");
    private static final String GATE_PREFIX = "/api/auth/gate";

    private final SiteAvailabilityService availabilityService;
    private final ObjectMapper objectMapper;
    private final Set<String> bypassAuthorities;

    public SiteAvailabilityFilter(
            SiteAvailabilityService availabilityService,
            ObjectMapper objectMapper,
            Set<String> bypassRoles
    ) {
        this.availabilityService = availabilityService;
        this.objectMapper = objectMapper;
        this.bypassAuthorities = bypassRoles.stream()
                .map(role -> "ROLE_" + role)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        if (!path.startsWith("/api/") && !path.startsWith("/ws/")) {
            return true;
        }

        return ALWAYS_OPEN.contains(path)
                || path.equals(GATE_PREFIX)
                || path.startsWith(GATE_PREFIX + "/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        SiteAvailabilityService.SiteStatus status = availabilityService.currentStatus();

        if (status.playerAccessEnabled() || hasBypassRole()) {
            filterChain.doFilter(request, response);
            return;
        }

        writeUnavailable(request, response, status);
    }

    private boolean hasBypassRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(bypassAuthorities::contains);
    }

    private void writeUnavailable(
            HttpServletRequest request,
            HttpServletResponse response,
            SiteAvailabilityService.SiteStatus status
    ) throws IOException {
        String message = status.message() == null || status.message().isBlank()
                ? DEFAULT_MESSAGE
                : status.message();

        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.RETRY_AFTER, "60");

        ApiErrorResponse body = new ApiErrorResponse(
                TimeTravelContext.offsetDateTimeNow(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                message,
                request.getRequestURI(),
                List.of(),
                "SITE_DISABLED"
        );

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
