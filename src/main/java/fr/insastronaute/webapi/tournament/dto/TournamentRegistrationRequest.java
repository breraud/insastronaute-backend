package fr.insastronaute.webapi.tournament.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TournamentRegistrationRequest(
        @NotNull(message = "Le joueur à inscrire est requis.")
        UUID userId
) {
}
