package fr.insastronaute.webapi.card.print.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CardPrintRunCreateRequest(
        @NotNull UUID templateId,
        @NotNull @Min(1) @Max(100) Integer sheetCount,
        @Valid @NotEmpty @Size(min = 9, max = 9) List<CardPrintSlotCardRequest> slots
) {
}
