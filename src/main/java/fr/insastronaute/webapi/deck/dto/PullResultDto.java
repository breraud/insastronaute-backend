package fr.insastronaute.webapi.deck.dto;

import java.util.List;
import java.util.UUID;

public record PullResultDto(
        UUID deckId,
        Long remainingTokens,
        List<PullDropDto> drops
) {
}
