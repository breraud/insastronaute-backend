package fr.insastronaute.webapi.card.print.dto;

import fr.insastronaute.webapi.card.print.domain.CardPrintFitMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CardPrintTemplateCalibrationRequest(
        @NotNull Boolean enabled,
        @NotNull CardPrintFitMode fitMode,
        @DecimalMin("0.0") @DecimalMax("20.0") Double bleedPt,
        @Valid @NotEmpty @Size(min = 9, max = 9) List<CardPrintTemplateSlotRequest> slots,
        @Valid @NotNull CardPrintCodeSettingsRequest code,
        @Valid @Size(min = 9, max = 9) List<CardPrintTemplateSlotRequest> backSlots,
        @Valid CardPrintQrSettingsRequest qr
) {
}
