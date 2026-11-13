package fr.insastronaute.webapi.tournament.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record TournamentAttendanceRequest(
        @NotNull(message = "La liste de présence est requise.")
        @Valid
        List<TournamentAttendanceEntryDto> entries
) {
}
