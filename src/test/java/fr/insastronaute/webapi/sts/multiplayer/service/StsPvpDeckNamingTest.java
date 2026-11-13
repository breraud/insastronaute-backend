package fr.insastronaute.webapi.sts.multiplayer.service;

import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpDeck;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce qui fait qu'enregistrer un deck remplace l'existant plutôt que d'en créer un double.
 */
class StsPvpDeckNamingTest {

    @Test
    void theSameNameMatchesWhateverItsCaseOrSurroundingSpaces() {
        assertThat(StsPvpDeckNaming.sameName("Aggro", "aggro")).isTrue();
        assertThat(StsPvpDeckNaming.sameName("  Aggro ", "AGGRO")).isTrue();
    }

    @Test
    void differentNamesDoNotMatch() {
        assertThat(StsPvpDeckNaming.sameName("Aggro", "Aggro 2")).isFalse();
        assertThat(StsPvpDeckNaming.sameName("Aggro", null)).isFalse();
    }

    /**
     * Le dépôt rend les decks du plus récent au plus ancien : le premier trouvé est celui qu'on
     * remplace, les suivants sont des doublons plus anciens.
     */
    @Test
    void keepsTheRepositoryOrderSoTheMostRecentComesFirst() {
        StsPvpDeck newest = deck("Aggro");
        StsPvpDeck other = deck("Contrôle");
        StsPvpDeck oldest = deck("aggro ");

        assertThat(StsPvpDeckNaming.sameNamed(List.of(newest, other, oldest), "AGGRO"))
                .containsExactly(newest, oldest);
    }

    @Test
    void findsNothingForANewName() {
        assertThat(StsPvpDeckNaming.sameNamed(List.of(deck("Aggro")), "Tempo")).isEmpty();
    }

    private static StsPvpDeck deck(String name) {
        StsPvpDeck deck = new StsPvpDeck();
        deck.setName(name);
        return deck;
    }
}
