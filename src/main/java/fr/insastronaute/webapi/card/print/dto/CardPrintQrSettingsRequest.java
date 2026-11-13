package fr.insastronaute.webapi.card.print.dto;

import fr.insastronaute.webapi.card.print.domain.CardPrintQrCorner;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CardPrintQrSettingsRequest(
        @NotNull Boolean enabled,
        @NotNull CardPrintQrCorner corner,
        @NotNull @DecimalMin("8.0") @DecimalMax("300.0") Double sizePt,
        @Min(0) @Max(8) Integer marginModules,
        @DecimalMin("0.0") @DecimalMax("150.0") Double insetPt
) {
}
