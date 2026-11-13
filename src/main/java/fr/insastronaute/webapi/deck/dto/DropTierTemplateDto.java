package fr.insastronaute.webapi.deck.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DropTierTemplateDto(
        UUID id,
        String name,
        Integer cardsPerPull,
        List<DropTierDto> dropTiers,
        Instant updatedAt
) {
}
