package fr.insastronaute.webapi.card.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CardInstanceBatchCreateRequest(
        @NotNull @Min(1) @Max(1000) Integer quantity
) {
}
