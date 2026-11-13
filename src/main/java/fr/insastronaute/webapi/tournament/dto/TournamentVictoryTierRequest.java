package fr.insastronaute.webapi.tournament.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record TournamentVictoryTierRequest(
        @NotBlank(message = "Le libellé du palier est requis.")
        @Size(max = 80, message = "Le libellé du palier ne peut pas dépasser 80 caractères.")
        String label,

        @NotNull(message = "Le nombre de points du palier est requis.")
        @Min(value = 0, message = "Les points de victoire ne peuvent pas être négatifs.")
        Integer points,

        @Size(max = 120, message = "Le nom d'équipe ne peut pas dépasser 120 caractères.")
        String teamName,

        @NotNull(message = "Un palier doit désigner au moins un vainqueur.")
        List<UUID> memberUserIds
) {
}
