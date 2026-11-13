package fr.insastronaute.webapi.place.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** La zone à recouvrir, et de quelle couleur. */
public record PlaceCensorRequest(
        @NotNull @PositiveOrZero Integer x,
        @NotNull @PositiveOrZero Integer y,
        // Bornée : une censure large est un reset déguisé, qui a son propre geste.
        @NotNull @Positive @Max(200) Integer width,
        @NotNull @Positive @Max(200) Integer height,
        @NotNull @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "La couleur doit être au format #RRGGBB.")
        String color
) {
}
