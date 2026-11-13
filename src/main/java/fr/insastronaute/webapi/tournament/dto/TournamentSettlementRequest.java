package fr.insastronaute.webapi.tournament.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record TournamentSettlementRequest(
        @NotNull(message = "Le nombre de points de participation est requis.")
        @Min(value = 0, message = "Les points de participation ne peuvent pas être négatifs.")
        Integer participationPoints,

        // Optional: lets the organiser save the final check-in and award in one call.
        @Valid
        List<TournamentAttendanceEntryDto> attendance,

        @Valid
        List<TournamentVictoryTierRequest> victoryTiers,

        // Defaults to true when omitted. Not named `notify`: a record component
        // cannot shadow Object.notify().
        Boolean notifyPlayers
) {
}
