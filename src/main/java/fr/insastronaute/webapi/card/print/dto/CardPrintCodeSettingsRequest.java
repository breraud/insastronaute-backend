package fr.insastronaute.webapi.card.print.dto;

import fr.insastronaute.webapi.card.print.domain.CardPrintFontFamily;
import fr.insastronaute.webapi.card.print.domain.CardPrintFontWeight;
import fr.insastronaute.webapi.card.print.domain.CardPrintTextAlign;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record CardPrintCodeSettingsRequest(
        @NotNull @DecimalMin("0.0") Double xPt,
        @NotNull @DecimalMin("0.0") Double yPt,
        @NotNull @Positive Double widthPt,
        @NotNull @Positive Double heightPt,
        @NotNull CardPrintFontFamily fontFamily,
        @NotNull CardPrintFontWeight fontWeight,
        @NotNull @DecimalMin("4.0") @DecimalMax("36.0") Double fontSizePt,
        @NotNull @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String backgroundColor,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double backgroundOpacity,
        @NotNull CardPrintTextAlign align
) {
}
