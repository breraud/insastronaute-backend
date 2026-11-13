package fr.insastronaute.webapi.card.dto;

import fr.insastronaute.webapi.card.domain.CardRevealAnimation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CardCategoryRequest(
        @NotBlank
        @Size(max = 50, message = "Le nom de catégorie doit contenir au plus 50 caractères.")
        String name,
        Integer displayOrder,
        Boolean hidden,
        Boolean game,
        @PositiveOrZero Integer firstAcquisitionPoints,
        @PositiveOrZero Integer duplicatePoints,
        // Nombre de doublons requis pour marquer duplicatePoints : 10 pour une commune, 1 ailleurs.
        @Positive Integer duplicatesPerPoint,
        Boolean extension,
        /** Absent vaut inchangé : un client plus ancien ne réinitialise pas le choix. */
        CardRevealAnimation revealAnimation
) {
    public CardCategoryRequest(
            String name, Integer displayOrder, Boolean hidden, Boolean game,
            Integer firstAcquisitionPoints, Integer duplicatePoints
    ) {
        this(name, displayOrder, hidden, game, firstAcquisitionPoints, duplicatePoints, null, null, null);
    }

    public CardCategoryRequest(String name, Integer displayOrder, Boolean hidden, Boolean game) {
        this(name, displayOrder, hidden, game, null, null, null, null, null);
    }
}
