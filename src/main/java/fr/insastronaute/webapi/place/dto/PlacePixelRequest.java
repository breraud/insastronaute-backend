package fr.insastronaute.webapi.place.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PlacePixelRequest(
        @NotNull Integer x,
        @NotNull Integer y,
        @NotNull @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "La couleur doit être au format #RRGGBB.") String color,
        /** Payer pour poser sans attendre le cooldown. Absent vaut non. */
        Boolean bonus
) {
    public PlacePixelRequest(Integer x, Integer y, String color) {
        this(x, y, color, false);
    }

    public boolean wantsBonus() {
        return Boolean.TRUE.equals(bonus);
    }
}
