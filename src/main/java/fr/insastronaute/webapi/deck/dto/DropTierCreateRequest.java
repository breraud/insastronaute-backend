package fr.insastronaute.webapi.deck.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DropTierCreateRequest(
        @Size(max = 120) String label,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal dropChancePercent,
        @Positive Integer maxCardsPerPull,
        @PositiveOrZero Integer minCardsPerPull,
        @NotEmpty List<@NotNull UUID> cardIds
) {
}
