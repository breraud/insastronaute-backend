package fr.insastronaute.webapi.place.dto;

import fr.insastronaute.webapi.place.domain.PlaceResizeMode;
import fr.insastronaute.webapi.place.domain.PlaceVisibilityMode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

public record PlaceConfigUpdateDto(
        boolean isActive,
        @NotNull Instant startDate,
        @NotNull Instant endDate,
        @Positive @Max(2000) int canvasWidth,
        @Positive @Max(2000) int canvasHeight,
        @Positive long baseCooldownMs,
        @Positive long challengeCooldownMs,
        @Positive int challengeRatio,
        // Boxés, et absents veut dire « inchangé » : un client qui ne connaît pas
        // encore ces champs continue de configurer le reste sans les écraser —
        // et sans se faire rejeter, Jackson refusant un primitif manquant.
        @PositiveOrZero Long bonusPixelPrice,
        @PositiveOrZero Long bonusPixelPriceIncrement,
        // Même raison que les deux précédents : absent vaut « inchangé ».
        Boolean showPixelAuthors,
        // Absent vaut « inchangé », pour la meme raison.
        PlaceVisibilityMode visibilityMode,
        // Absent vaut « inchangé », comme les precedents.
        Boolean departmentCanvasEnabled,
        // Absent vaut « inchange », comme les precedents.
        Boolean resultsVisible,
        PlaceResizeMode resizeMode
) {
    @AssertTrue(message = "La date de fin doit être postérieure à la date de début.")
    @SuppressWarnings("unused") // Invoked by Jakarta Bean Validation through reflection.
    public boolean isValidWindow() {
        return startDate == null || endDate == null || endDate.isAfter(startDate);
    }
}
