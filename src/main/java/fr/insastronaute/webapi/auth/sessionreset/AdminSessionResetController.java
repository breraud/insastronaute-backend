package fr.insastronaute.webapi.auth.sessionreset;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reattribuer les roles du staff sans attendre huit heures.
 *
 * <p>La route vit sous {@code /api/admin}, donc sous la regle generale qui la reserve aux
 * administrateurs.</p>
 */
@RestController
@RequestMapping("/api/admin/sessions")
@Tag(name = "Admin Sessions")
public class AdminSessionResetController {

    private final StaffSessionResetService resetService;

    public AdminSessionResetController(StaffSessionResetService resetService) {
        this.resetService = resetService;
    }

    @PostMapping("/reset-staff")
    @Operation(
            summary = "Expire every staff session so role changes take effect",
            description = "Roles are read once at login and frozen for the session's eight hours, so reassigning them "
                    + "in Keycloak changes nothing for whoever is already connected. This expires staff sessions: each "
                    + "one ends at its next request and comes back through Keycloak with the roles it has now, "
                    + "removals included. Players are untouched. The caller's own session goes too."
    )
    public ResetResultDto resetStaffSessions() {
        long epoch = resetService.reset();
        return new ResetResultDto(epoch, "Les sessions du staff sont périmées. "
                + "Chacune repassera par Keycloak à sa prochaine requête, la tienne comprise.");
    }

    public record ResetResultDto(long epoch, String message) {}
}
