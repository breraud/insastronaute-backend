package fr.insastronaute.webapi.card.print.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CardPrintTemplateSlotRequest(
        @NotNull @Min(0) @Max(8) Integer slotIndex,
        @NotNull @DecimalMin("0.0") Double xPt,
        @NotNull @DecimalMin("0.0") Double yPt,
        @NotNull @Positive Double widthPt,
        @NotNull @Positive Double heightPt
) {
}
