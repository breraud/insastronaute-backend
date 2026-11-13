package fr.insastronaute.webapi.card.print.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CardPrintSlotCardRequest(
        @NotNull @Min(0) @Max(8) Integer slotIndex,
        @NotNull UUID cardId
) {
}
