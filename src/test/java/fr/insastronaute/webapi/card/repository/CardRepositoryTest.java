package fr.insastronaute.webapi.card.repository;

import fr.insastronaute.webapi.card.domain.Card;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CardRepositoryTest {

    @Autowired
    private CardRepository cardRepository;

    @Test
    void savesDefaultCardWithFixedIdAsNewEntity() {
        Card card = new Card();
        card.setId(Card.DEFAULT_CARD_ID);
        card.setName("Card cover");
        card.setCategory(Card.COVER_CATEGORY);
        card.setDefaultCard(true);
        card.setEnabled(true);
        card.setDisplayOrder(0);
        card.setImageObjectKey("cards/default/original.jpg");
        card.setThumbnailObjectKey("cards/default/thumbnail.jpg");
        card.setImageUrl("https://s3.example.com/cards/default/original.jpg");
        card.setThumbnailUrl("https://s3.example.com/cards/default/thumbnail.jpg");
        card.setContentType("image/jpeg");

        Card saved = cardRepository.saveAndFlush(card);

        assertThat(saved.getId()).isEqualTo(Card.DEFAULT_CARD_ID);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(cardRepository.findById(Card.DEFAULT_CARD_ID)).isPresent();
    }
}
