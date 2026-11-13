package fr.insastronaute.webapi.deck.dto;

import fr.insastronaute.webapi.card.dto.CardDto;
import fr.insastronaute.webapi.deck.domain.DropTierRarity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DropTierDto(
        UUID id,
        String label,
        BigDecimal dropChancePercent,
        Integer maxCardsPerPull,
        Integer minCardsPerPull,
        List<CardDto> cards,
        /** Chance de voir ce palier au moins une fois en une ouverture, en %. */
        Double pullProbabilityPercent,
        /** Nombre moyen d'ouvertures avant d'en voir un. */
        Double expectedPullsForOne,
        /** La bande qui pilote l'animation de révélation. */
        DropTierRarity rarity
) {
    /** Un palier sans contexte de tirage : la rareté ressentie ne se calcule pas. */
    public DropTierDto(UUID id, String label, BigDecimal dropChancePercent, Integer maxCardsPerPull,
                       Integer minCardsPerPull, List<CardDto> cards) {
        this(id, label, dropChancePercent, maxCardsPerPull, minCardsPerPull, cards, null, null, null);
    }
}
