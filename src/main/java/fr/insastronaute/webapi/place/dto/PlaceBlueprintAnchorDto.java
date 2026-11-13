package fr.insastronaute.webapi.place.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Le coin haut-gauche du plan sur la fresque. Borné comme le canevas lui-même. */
public record PlaceBlueprintAnchorDto(
        @NotNull @PositiveOrZero @Max(1999) Integer x,
        @NotNull @PositiveOrZero @Max(1999) Integer y
) {}
