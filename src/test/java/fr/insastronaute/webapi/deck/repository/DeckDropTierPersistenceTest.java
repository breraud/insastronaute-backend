package fr.insastronaute.webapi.deck.repository;

import fr.insastronaute.webapi.deck.domain.Deck;
import fr.insastronaute.webapi.deck.domain.DropTier;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Remplacer les paliers d'un pack doit remplacer, pas empiler.
 *
 * Le service reconstruit des paliers neufs à chaque enregistrement et les pose
 * dans la collection existante ; seule une vraie base dit si les anciens sont
 * bien partis, ce qu'un dépôt simulé ne peut pas montrer.
 */
@DataJpaTest
@ActiveProfiles("test")
class DeckDropTierPersistenceTest {

    @Autowired
    private DeckRepository deckRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void replacingTheTiersOfADeckDoesNotStackThemUp() {
        Deck deck = new Deck();
        deck.setName("Starter");
        deck.setPrice(10L);
        deck.setCardsPerPull(1);
        deck.setImageUrl("decks/starter.jpg");
        deck.setDropTiers(List.of(tier("Commune", 70), tier("Rare", 30)));
        UUID deckId = deckRepository.saveAndFlush(deck).getId();
        entityManager.clear();

        Deck reloaded = deckRepository.findWithDropTiersById(deckId).orElseThrow();
        reloaded.setDropTiers(List.of(tier("Commune", 60), tier("Rare", 40)));
        deckRepository.saveAndFlush(reloaded);
        entityManager.clear();

        Deck afterUpdate = deckRepository.findWithDropTiersById(deckId).orElseThrow();
        assertThat(afterUpdate.getDropTiers()).extracting(DropTier::getLabel)
                .containsExactlyInAnyOrder("Commune", "Rare");
        assertThat(entityManager.createQuery("select count(t) from DropTier t", Long.class).getSingleResult())
                .isEqualTo(2L);
    }

    private static DropTier tier(String label, int chance) {
        DropTier tier = new DropTier();
        tier.setLabel(label);
        tier.setDropChancePercent(BigDecimal.valueOf(chance));
        tier.setDisplayOrder(0);
        return tier;
    }
}
