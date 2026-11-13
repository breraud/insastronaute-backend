package fr.insastronaute.webapi.deck.dto;

import fr.insastronaute.webapi.card.dto.CardDto;
import fr.insastronaute.webapi.deck.domain.DropTierRarity;

import java.util.UUID;

/**
 * @param rarity à quel point ce palier sort de l'ordinaire dans CE pack, la
 *               seule mesure comparable d'un pack à l'autre : le pourcentage
 *               brut ne dit rien tant qu'on ignore combien de cartes sont tirées
 * @param expectedPullsForOne ouvertures moyennes avant d'en voir un
 */
public record PullDropDto(
        UUID dropTierId,
        String dropTierLabel,
        CardDto card,
        DropTierRarity rarity,
        Double expectedPullsForOne
) {
    public PullDropDto(UUID dropTierId, String dropTierLabel, CardDto card) {
        this(dropTierId, dropTierLabel, card, null, null);
    }
}
