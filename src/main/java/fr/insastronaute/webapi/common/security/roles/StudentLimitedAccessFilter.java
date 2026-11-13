package fr.insastronaute.webapi.common.security.roles;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.web.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Rejects categorically forbidden limited-student routes before MVC binds request arguments.
 */
public final class StudentLimitedAccessFilter extends OncePerRequestFilter {

    private static final Pattern EVENT_REGISTRATION =
            Pattern.compile("^/api/events/[^/]+/registrations/?$");
    private static final Pattern EVENT_ANSWERS =
            Pattern.compile("^/api/events/[^/]+/registrations/answers/?$");
    private static final Pattern MY_PHYSICAL_COPIES =
            Pattern.compile("^/api/cards/[^/]+/instances/mine/?$");

    private final ObjectMapper objectMapper;

    public StudentLimitedAccessFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && StudentLimitedAccess.isLimited(authentication.getAuthorities())
                && isForbiddenRequest(request)) {
            writeForbidden(request, response);
            return;
        }

        chain.doFilter(request, response);
    }

    private static boolean isForbiddenRequest(HttpServletRequest request) {
        String path = request.getRequestURI();

        if (segmentPrefix(path, "/api/challenges")
                || segmentPrefix(path, "/api/treasure-hunt")
                || segmentPrefix(path, "/api/place")
                || (request.getMethod().equalsIgnoreCase("POST") && segmentPrefix(path, "/api/cards/instances/claim"))
                || (request.getMethod().equalsIgnoreCase("POST") && segmentPrefix(path, "/api/cards/instances/release"))
                || (request.getMethod().equalsIgnoreCase("GET") && MY_PHYSICAL_COPIES.matcher(path).matches())) {
            return true;
        }

        if (request.getMethod().equalsIgnoreCase("GET") && segmentPrefix(path, "/api/cards/deck")) {
            String collectionType = request.getParameter("collectionType");
            String scope = request.getParameter("scope");
            if ("PHYSICAL".equalsIgnoreCase(collectionType) || "TEAM".equalsIgnoreCase(scope)) {
                return true;
            }
        }

        return switch (request.getMethod()) {
            case "POST", "DELETE" -> EVENT_REGISTRATION.matcher(path).matches();
            case "PUT" -> EVENT_ANSWERS.matcher(path).matches();
            default -> false;
        };
    }

    private static boolean segmentPrefix(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private void writeForbidden(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpStatus status = HttpStatus.FORBIDDEN;
        StudentLimitedActionForbiddenException exception = new StudentLimitedActionForbiddenException();

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(
                TimeTravelContext.offsetDateTimeNow(),
                status.value(),
                status.getReasonPhrase(),
                exception.getMessage(),
                request.getRequestURI(),
                List.of(),
                "STUDENT_LIMITED_ACTION_FORBIDDEN"
        ));
    }
}
