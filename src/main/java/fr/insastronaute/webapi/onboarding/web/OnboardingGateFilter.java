package fr.insastronaute.webapi.onboarding.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.web.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
 * Ferme l'API aux comptes authentifies qui n'ont pas encore le role STUDENT.
 *
 * <p>Il est enregistre apres {@code AuthorizationFilter} : l'authentification est
 * resolue, les autorites sont dans le contexte, et seules les requetes deja
 * autorisees arrivent ici.</p>
 *
 * <p>Un compte fraichement cree n'a aucun role tant qu'un respo ne l'a pas valide.
 * Avant ce filtre, rien ne verifiait STUDENT : ce garde est ce qui ferme la porte.</p>
 */
public class OnboardingGateFilter extends OncePerRequestFilter {

    private static final String STUDENT_AUTHORITY = "ROLE_STUDENT";
    private static final String STUDENT_LIMITED_AUTHORITY = "ROLE_STUDENT_LIMITED";
    private static final Set<String> ALWAYS_OPEN = Set.of(
            "/api/session", "/api/csrf", "/api/auth/refresh-roles"
    );
    /**
     * Le push s'abonne avant la validation, deliberement.
     *
     * <p>La notification la plus utile qu'un compte en attente puisse recevoir est
     * justement celle qui lui apprend qu'il vient d'etre valide - et ce moment est
     * precisement celui ou il a ferme l'application. Pour la recevoir, il faut s'etre
     * abonne avant ; exiger STUDENT ici rendrait cette notification impossible.
     * Abonner son propre appareil a ses propres notifications ne divulgue rien et
     * n'accorde rien.</p>
     */
    private static final String PUSH_PREFIX = "/api/push";
    private static final String GATE_PREFIX = "/api/auth/gate";
    private static final String ONBOARDING_PREFIX = "/api/onboarding";

    private final ObjectMapper objectMapper;
    private final Set<String> bypassAuthorities;

    public OnboardingGateFilter(ObjectMapper objectMapper, Set<String> bypassRoles) {
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
                || segmentPrefix(path, PUSH_PREFIX)
                || segmentPrefix(path, GATE_PREFIX)
                || segmentPrefix(path, ONBOARDING_PREFIX);
    }

    private static boolean segmentPrefix(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (allowed()) {
            chain.doFilter(request, response);
            return;
        }
        writeForbidden(request, response);
    }

    private boolean allowed() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Un appelant anonyme arrive ici uniquement si l'autorisation l'a deja laisse
        // passer, donc l'endpoint est public : /api/scan par exemple. L'accueil garde
        // les joueurs authentifies, pas le monde entier : bloquer ici retirerait au
        // public des pages qui ne l'ont jamais demande.
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return true;
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> STUDENT_AUTHORITY.equals(authority)
                        || STUDENT_LIMITED_AUTHORITY.equals(authority)
                        || bypassAuthorities.contains(authority));
    }

    private void writeForbidden(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiErrorResponse body = new ApiErrorResponse(
                TimeTravelContext.offsetDateTimeNow(),
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "Ton inscription doit etre validee par un respo.",
                request.getRequestURI(),
                List.of(),
                "ONBOARDING_REQUIRED"
        );

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
