package fr.insastronaute.webapi.tournament.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TournamentAttendanceEntryDto(
        @NotNull(message = "L'identifiant du joueur est requis.")
        UUID userId,

        // null clears the check-in and puts the player back in the "not marked" state.
        Boolean attended
) {
}
