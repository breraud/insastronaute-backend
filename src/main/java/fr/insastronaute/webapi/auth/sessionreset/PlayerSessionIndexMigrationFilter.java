package fr.insastronaute.webapi.auth.sessionreset;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.web.ApiErrorResponse;
import fr.insastronaute.webapi.user.domain.OnboardingStatus;
import fr.insastronaute.webapi.user.repository.UserRepository;
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
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rattrape les sessions joueur creees avant l'activation du depot Redis indexe.
 *
 * <p>Modifier un attribut force leur sauvegarde par le nouveau depot et cree donc
 * l'index par sujet OIDC utilise lors d'un refus. Une session deja refusee ne doit pas
 * etre indexee puis laisser passer une requete : elle est fermee avant l'autorisation.</p>
 */
public class PlayerSessionIndexMigrationFilter extends OncePerRequestFilter {

    static final String SESSION_ATTRIBUTE = "insastronaute.playerSessionIndexed";
    private static final String STUDENT_AUTHORITY = "ROLE_STUDENT";
    private static final String STUDENT_LIMITED_AUTHORITY = "ROLE_STUDENT_LIMITED";

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Set<String> staffAuthorities;

    public PlayerSessionIndexMigrationFilter(
            UserRepository userRepository,
            ObjectMapper objectMapper,
            Set<String> staffRoles
    ) {
        this.userRepository = userRepository;
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
        if (!isOrdinaryPlayer(authentication)) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        if (session == null || Boolean.TRUE.equals(session.getAttribute(SESSION_ATTRIBUTE))) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean rejected = userRepository.findByKeycloakSub(authentication.getName())
                .map(user -> user.getOnboardingStatus() == OnboardingStatus.REJECTED)
                .orElse(false);
        if (rejected) {
            session.invalidate();
            SecurityContextHolder.clearContext();
            writeRevoked(request, response);
            return;
        }

        // RedisIndexedSessionRepository ne met a jour l'index que lorsque le principal
        // explicite ou SPRING_SECURITY_CONTEXT figure dans son delta. Le marqueur seul
        // sauvegarderait bien la session sans jamais la rendre trouvable par sujet.
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext()
        );
        session.setAttribute(SESSION_ATTRIBUTE, Boolean.TRUE);
        filterChain.doFilter(request, response);
    }

    private boolean isOrdinaryPlayer(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return (authorities.contains(STUDENT_AUTHORITY) || authorities.contains(STUDENT_LIMITED_AUTHORITY))
                && authorities.stream().noneMatch(staffAuthorities::contains);
    }

    private void writeRevoked(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiErrorResponse body = new ApiErrorResponse(
                TimeTravelContext.offsetDateTimeNow(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Ton inscription a ete refusee, reconnecte-toi.",
                request.getRequestURI(),
                List.of(),
                "ONBOARDING_SESSION_REVOKED"
        );
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
