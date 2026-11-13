package fr.insastronaute.webapi.tournament.controller;

import fr.insastronaute.webapi.tournament.dto.AdminTournamentDetailDto;
import fr.insastronaute.webapi.tournament.dto.AdminTournamentSummaryDto;
import fr.insastronaute.webapi.tournament.dto.TournamentAttendanceRequest;
import fr.insastronaute.webapi.tournament.dto.TournamentRegistrationRequest;
import fr.insastronaute.webapi.tournament.dto.TournamentSettlementRequest;
import fr.insastronaute.webapi.tournament.dto.TournamentSettlementResultDto;
import fr.insastronaute.webapi.tournament.service.TournamentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/tournaments")
@Tag(name = "Admin Tournaments")
public class AdminTournamentController {

    private final TournamentService tournamentService;

    public AdminTournamentController(TournamentService tournamentService) {
        this.tournamentService = tournamentService;
    }

    @GetMapping
    @Operation(
            summary = "List tournaments",
            description = "Returns every TOURNAMENT event with its registration, attendance, and settlement state, most recent first."
    )
    public List<AdminTournamentSummaryDto> listTournaments() {
        return tournamentService.listTournaments();
    }

    @GetMapping("/{eventId}")
    @Operation(
            summary = "Get one tournament",
            description = "Returns the tournament with its participants, their check-in state, the points already granted, and the saved victory tiers."
    )
    public AdminTournamentDetailDto getTournament(@PathVariable UUID eventId) {
        return tournamentService.getTournament(eventId);
    }

    @PutMapping("/{eventId}/attendance")
    @Operation(
            summary = "Save tournament attendance",
            description = "Marks players present or absent without granting any point. Send attended=null to clear a check-in."
    )
    public AdminTournamentDetailDto updateAttendance(
            @PathVariable UUID eventId,
            @Valid @RequestBody TournamentAttendanceRequest request
    ) {
        return tournamentService.updateAttendance(eventId, request);
    }

    /**
     * Plus etroit que le reste de cet ecran : pointer une presence ne fait entrer
     * personne, inscrire donne une place que les regles d'inscription auraient pu
     * refuser. Ce geste revient a ceux qui tiennent les tournois.
     */
    @PostMapping("/{eventId}/registrations")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESPO_TOURNOIS', 'SOUS_RESPO_TOURNOIS')")
    @Operation(
            summary = "Register a player by hand",
            description = "Registers a player who did not sign up, whatever the registration window, the capacity "
                    + "or the targeted departments. The player always gets a confirmed seat: a waitlisted player "
                    + "is confirmed, an already registered one is left untouched."
    )
    public AdminTournamentDetailDto registerParticipant(
            @PathVariable UUID eventId,
            @Valid @RequestBody TournamentRegistrationRequest request
    ) {
        return tournamentService.registerParticipant(eventId, request.userId());
    }

    @PostMapping("/{eventId}/settle")
    @Operation(
            summary = "Grant tournament points",
            description = "Grants participation points to every player marked present plus the victory bonuses, then notifies them. "
                    + "Re-running applies only the difference, so validating twice never stacks points."
    )
    public TournamentSettlementResultDto settle(
            @PathVariable UUID eventId,
            @Valid @RequestBody TournamentSettlementRequest request,
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        return tournamentService.settle(eventId, request, oidcUser == null ? null : oidcUser.getSubject());
    }

    @DeleteMapping("/{eventId}/settlement")
    @Operation(
            summary = "Cancel a tournament settlement",
            description = "Takes every granted point back off the players' balances and clears the award ledger. Check-in state is kept."
    )
    public AdminTournamentDetailDto cancelSettlement(@PathVariable UUID eventId) {
        return tournamentService.cancelSettlement(eventId);
    }
}
