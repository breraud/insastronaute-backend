package fr.insastronaute.webapi.deck.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record DropTierOrderUpdateRequest(
        @NotEmpty List<UUID> tierIds
) {
}
