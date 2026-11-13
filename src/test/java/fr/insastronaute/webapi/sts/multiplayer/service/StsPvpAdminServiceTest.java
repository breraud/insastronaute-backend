package fr.insastronaute.webapi.sts.multiplayer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpDeck;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpAdminDtos;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce que l'administration montre d'un joueur : quel deck un duel utiliserait vraiment, et de
 * quelles cartes il est fait.
 */
class StsPvpAdminServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void theActiveDeckOfTheSelectedCharacterIsTheOneABattleUses() {
        StsPvpDeck recent = deck("EP", false);
        StsPvpDeck active = deck("EP", true);

        assertThat(StsPvpAdminService.deckUsedInBattle(List.of(recent, active), "EP"))
                .isEqualTo(active.getId());
    }

    /**
     * Le cas que le seul drapeau « actif » cacherait : un joueur qui n'a marqué aucun deck joue
     * quand même, avec le plus récent. L'administration regarderait sinon un deck au hasard, ou
     * aucun.
     */
    @Test
    void withoutAnActiveDeckABattleUsesTheMostRecentOne() {
        StsPvpDeck mostRecent = deck("EP", false);
        StsPvpDeck older = deck("EP", false);

        assertThat(StsPvpAdminService.deckUsedInBattle(List.of(mostRecent, older), "EP"))
                .isEqualTo(mostRecent.getId());
    }

    /** Un deck actif d'un autre personnage ne sert pas : le combat ne regarde que celui choisi. */
    @Test
    void aDeckOfAnotherCharacterIsNeverTheOneUsed() {
        StsPvpDeck otherActive = deck("MECA", true);
        StsPvpDeck mine = deck("EP", false);

        assertThat(StsPvpAdminService.deckUsedInBattle(List.of(otherActive, mine), "EP"))
                .isEqualTo(mine.getId());
    }

    @Test
    void noDeckForTheSelectedCharacterMeansNoDeckUsed() {
        assertThat(StsPvpAdminService.deckUsedInBattle(List.of(deck("MECA", true)), "EP")).isNull();
        assertThat(StsPvpAdminService.deckUsedInBattle(List.of(deck("EP", true)), null)).isNull();
    }

    /** Trois exemplaires se lisent « 3× », pas trois lignes identiques : c'est ce qui s'équilibre. */
    @Test
    void groupsCopiesOfTheSameCardInOrderOfFirstAppearance() {
        ArrayNode cardIds = objectMapper.createArrayNode()
                .add("Frappe").add("Défense").add("Frappe").add(" ").add("Frappe");

        assertThat(StsPvpAdminService.groupCards(cardIds)).containsExactly(
                new StsPvpAdminDtos.DeckCard("Frappe", 3),
                new StsPvpAdminDtos.DeckCard("Défense", 1));
    }

    @Test
    void anUnreadableCardListIsAnEmptyDeck() {
        assertThat(StsPvpAdminService.groupCards(null)).isEmpty();
        assertThat(StsPvpAdminService.groupCards(objectMapper.createObjectNode())).isEmpty();
    }

    private static StsPvpDeck deck(String character, boolean active) {
        StsPvpDeck deck = new StsPvpDeck();
        ReflectionTestUtils.setField(deck, "id", UUID.randomUUID());
        deck.setSelectedCharacter(character);
        deck.setActive(active);
        return deck;
    }
}
