package fr.insastronaute.webapi.card.print.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CardPrintPreviewRequest(
        @Valid @NotEmpty @Size(min = 9, max = 9) List<CardPrintSlotCardRequest> slots
) {
}
