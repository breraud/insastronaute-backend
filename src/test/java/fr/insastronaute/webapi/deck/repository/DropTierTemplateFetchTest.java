package fr.insastronaute.webapi.deck.repository;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.deck.domain.DropTierTemplate;
import fr.insastronaute.webapi.deck.domain.DropTierTemplateTier;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un modèle relu doit rendre ses paliers une fois chacun.
 *
 * Charger deux collections imbriquées dans un même graphe produit un produit
 * cartésien : un palier de onze cartes ressortait onze fois, et le modèle
 * affichait « 22 paliers » là où l'admin en avait saisi quatre.
 */
@DataJpaTest
@ActiveProfiles("test")
class DropTierTemplateFetchTest {

    @Autowired
    private DropTierTemplateRepository templateRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void aTierIsReadBackOnceWhateverTheNumberOfCardsItHolds() {
        List<Card> many = IntStream.range(0, 11).mapToObj(index -> card("Commune " + index)).toList();
        List<Card> few = IntStream.range(0, 5).mapToObj(index -> card("Rare " + index)).toList();

        DropTierTemplate template = new DropTierTemplate();
        template.setName("Pack de départ");
        template.setCardsPerPull(5);
        template.setTiers(List.of(tier("Classique", 91, many), tier("Stellaire", 9, few)));
        UUID templateId = templateRepository.saveAndFlush(template).getId();
        entityManager.clear();

        assertThat(templateRepository.findWithTiersById(templateId).orElseThrow().getTiers())
                .extracting(DropTierTemplateTier::getLabel)
                .containsExactly("Classique", "Stellaire");

        entityManager.clear();

        assertThat(templateRepository.findAllByOrderByNameAsc())
                .singleElement()
                .satisfies(found -> assertThat(found.getTiers())
                        .extracting(DropTierTemplateTier::getLabel)
                        .containsExactly("Classique", "Stellaire"));
    }

    private Card card(String name) {
        Card card = new Card();
        card.setName(name);
        card.setCategory("Default");
        card.setEnabled(true);
        card.setDefaultCard(false);
        card.setDisplayOrder(0);
        card.setImageObjectKey("cards/" + name + "/original.jpg");
        card.setThumbnailObjectKey("cards/" + name + "/thumbnail.jpg");
        card.setImageUrl("https://s3.example.com/" + name + ".jpg");
        card.setThumbnailUrl("https://s3.example.com/" + name + "-thumb.jpg");
        card.setContentType("image/jpeg");
        return cardRepository.saveAndFlush(card);
    }

    private static DropTierTemplateTier tier(String label, int chance, List<Card> cards) {
        DropTierTemplateTier tier = new DropTierTemplateTier();
        tier.setLabel(label);
        tier.setDropChancePercent(BigDecimal.valueOf(chance));
        tier.setDisplayOrder(0);
        tier.setCards(cards);
        return tier;
    }
}
