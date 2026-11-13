package fr.insastronaute.webapi.card.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CardCollectionSettingsRequest(
        @NotNull(message = "Le multiplicateur physique est requis.")
        @Min(value = 0, message = "Le multiplicateur ne peut pas être négatif.")
        Integer physicalFirstAcquisitionMultiplier,

        @NotNull(message = "Le réglage des doublons physiques est requis.")
        Boolean physicalDuplicatesEnabled,

        @NotNull(message = "Le nombre de tokens par point est requis.")
        @Min(value = 0, message = "Le nombre de tokens par point ne peut pas être négatif.")
        Integer tokensPerPoint
) {
}
