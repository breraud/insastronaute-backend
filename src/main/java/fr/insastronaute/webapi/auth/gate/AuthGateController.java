package fr.insastronaute.webapi.auth.gate;

import fr.insastronaute.webapi.auth.gate.dto.GateEnterResponse;
import fr.insastronaute.webapi.auth.gate.dto.GateStatusResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/api/auth/gate")
public class AuthGateController {

    private static final String TICKET_COOKIE_NAME = "auth_ticket";
    private final AuthGateService authGateService;

    public AuthGateController(AuthGateService authGateService) {
        this.authGateService = authGateService;
    }

    @PostMapping("/enter")
    public ResponseEntity<GateEnterResponse> enter(
            @RequestBody(required = false) EnterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String existingTicket = extractTicketCookie(httpRequest);
        String redirectUri = request != null ? request.redirectUri() : null;

        GateEnterResponse response = authGateService.enter(existingTicket, redirectUri);
        if (response.ticketId() != null) {
            setTicketCookie(httpResponse, response.ticketId());
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Le passage prioritaire : un code, et on saute la file.
     *
     * Il n'authentifie personne. Le franchir, c'est arriver devant Keycloak comme tout le
     * monde, et Keycloak decide seul de la suite. Ce que le code achete, c'est de ne pas
     * attendre derriere deux mille etudiants quand il y a une urgence a traiter dans le
     * panneau d'administration.
     *
     * <p>Un code faux repond 403 sans rien dire de plus : ni « code inconnu », ni « fonction
     * desactivee ». Distinguer les deux apprendrait a qui tatonne si le raccourci existe.</p>
     */
    @PostMapping("/fast-pass")
    public ResponseEntity<GateEnterResponse> fastPass(
            @RequestBody(required = false) FastPassRequest request,
            HttpServletResponse httpResponse
    ) {
        if (request == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return authGateService.admitWithFastPass(request.code(), request.redirectUri())
                .map(response -> {
                    if (response.ticketId() != null) {
                        setTicketCookie(httpResponse, response.ticketId());
                    }
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @GetMapping("/status")
    public ResponseEntity<GateStatusResponse> status(
            @RequestParam(required = false) String ticketId,
            @RequestParam(required = false) String redirectUri,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String effectiveTicket = (ticketId != null && !ticketId.isBlank()) ? ticketId : extractTicketCookie(httpRequest);
        GateStatusResponse response = authGateService.getStatus(effectiveTicket, redirectUri);
        if (response.ticketId() != null) {
            setTicketCookie(httpResponse, response.ticketId());
        }
        return ResponseEntity.ok(response);
    }

    private String extractTicketCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> TICKET_COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void setTicketCookie(HttpServletResponse response, String ticketId) {
        Cookie cookie = new Cookie(TICKET_COOKIE_NAME, ticketId);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(600); // 10 minutes
        response.addCookie(cookie);
    }

    public record EnterRequest(String redirectUri) {}

    public record FastPassRequest(String code, String redirectUri) {}
}
