package fr.insastronaute.webapi.common.timetravel;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.common.web.ApiErrorResponse;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

/**
 * Le voyage dans le temps : une date forcee, un departement force, pour la
 * duree d'une requete et pour le staff seulement.
 *
 * <p><strong>Une valeur que ce filtre ne comprend pas est un 400, jamais un
 * silence.</strong> Les deux en-tetes etaient auparavant ignores quand ils ne
 * se lisaient pas - une date mal formee tombait dans un {@code catch} vide, un
 * departement inconnu ne trouvait rien et laissait le vrai en place. Le
 * symptome etait alors le pire possible : la page repond normalement, avec les
 * donnees reelles, et rien nulle part ne dit que le reglage n'a pas pris. Un
 * en-tete de debug qui ne sert qu'a des gens qui savent ce qu'ils font doit
 * dire quand on le nourrit mal.</p>
 *
 * <p><strong>Sauf sur les routes par lesquelles on se rattrape.</strong>
 * {@code /api/session} et {@code /api/csrf} ignorent un en-tete illisible au
 * lieu de le refuser, et c'est la meme regle que la liste blanche de
 * {@code SiteAvailabilityFilter}, pour la meme raison. Le panneau de reglage
 * vit dans le front, et le front ne l'affiche qu'a un membre du staff
 * reconnu : si la session elle-meme repondait 400, il n'y aurait plus de
 * panneau, donc plus de bouton pour effacer la valeur fautive, et la seule
 * sortie serait de vider le stockage local a la main. Un garde-fou dont on ne
 * peut pas sortir n'est pas un garde-fou.</p>
 *
 * <p>Le departement est resolu ici, une fois, en identifiant : par son
 * identifiant, ou par son nom de groupe Keycloak. Les deux sont acceptes parce
 * que les deux ont circule - le panneau du front envoie l'identifiant, que
 * {@code /api/departments} publie, et le nom de groupe reste utilisable a la
 * main.</p>
 */
@Component
public class TimeTravelFilter extends OncePerRequestFilter {

    /** Les routes qui doivent repondre meme quand le reglage est fautif : voir plus haut. */
    private static final Set<String> RECOVERY_PATHS = Set.of("/api/session", "/api/csrf");

    private final DepartmentRepository departmentRepository;
    private final ObjectMapper objectMapper;

    public TimeTravelFilter(DepartmentRepository departmentRepository, ObjectMapper objectMapper) {
        this.departmentRepository = departmentRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && hasStaffOrAdminRole(auth)) {
            String timeHeader = request.getHeader("X-Time-Travel-Date");
            String deptHeader = request.getHeader("X-Time-Travel-Dept");

            if (timeHeader != null && !timeHeader.isBlank()) {
                Instant forcedTime;
                try {
                    forcedTime = Instant.parse(timeHeader);
                } catch (DateTimeParseException exception) {
                    if (!isRecoveryPath(request)) {
                        writeBadRequest(request, response, "TIME_TRAVEL_INVALID_DATE",
                                "X-Time-Travel-Date n'est pas un instant ISO-8601 : " + timeHeader);
                        return;
                    }
                    forcedTime = null;
                }
                if (forcedTime != null) {
                    TimeTravelContext.setForcedTime(forcedTime);
                }
            }

            if (deptHeader != null && !deptHeader.isBlank()) {
                Optional<Department> department = resolveDepartment(deptHeader);
                if (department.isEmpty() && !isRecoveryPath(request)) {
                    TimeTravelContext.clear();
                    writeBadRequest(request, response, "TIME_TRAVEL_UNKNOWN_DEPARTMENT",
                            "X-Time-Travel-Dept ne designe aucun departement : " + deptHeader);
                    return;
                }
                department.ifPresent(found -> TimeTravelContext.setForcedDepartmentId(found.getId()));
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TimeTravelContext.clear();
        }
    }

    private boolean isRecoveryPath(HttpServletRequest request) {
        return RECOVERY_PATHS.contains(request.getRequestURI());
    }

    /** Par identifiant si l'en-tete en est un, par nom de groupe Keycloak sinon. */
    private Optional<Department> resolveDepartment(String header) {
        String value = header.trim();
        try {
            return departmentRepository.findById(UUID.fromString(value));
        } catch (IllegalArgumentException notAnId) {
            return departmentRepository.findByKeycloakGroupNameIgnoreCase(value);
        }
    }

    private void writeBadRequest(
            HttpServletRequest request, HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), new ApiErrorResponse(
                TimeTravelContext.offsetDateTimeNow(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                message,
                request.getRequestURI(),
                List.of(),
                code));
    }

    private boolean hasStaffOrAdminRole(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(r -> r.equals("ROLE_ADMIN") || r.equals("ROLE_STAFF"));
    }
}
