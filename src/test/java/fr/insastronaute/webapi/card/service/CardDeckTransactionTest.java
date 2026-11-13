package fr.insastronaute.webapi.card.service;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardCategory;
import fr.insastronaute.webapi.card.domain.CardCollectionScope;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.domain.CardGrouping;
import fr.insastronaute.webapi.card.dto.CardDeckDto;
import fr.insastronaute.webapi.card.repository.CardCategoryRepository;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * La collection lue hors de toute transaction.
 *
 * `open-in-view` est desactive : passe le retour du depot, les entites sont detachees
 * et la categorie -- chargee paresseusement -- n'est plus qu'un mandataire mort. Le
 * service doit donc porter lui-meme sa transaction. Le 31 aout, une methode inseree
 * juste sous l'annotation `@Transactional` de `getDeck` la lui a prise : la surcharge
 * appelee par le controleur s'est retrouvee nue, et `/api/cards/deck` a rendu 500 a
 * chaque joueur dont une carte porte une categorie.
 *
 * Les tests existants ne pouvaient pas le voir. Ceux du service donnent des depots
 * simules, qui rendent des entites completes sans mandataire ; ceux du controleur
 * simulent le service. Aucun ne traverse un vrai EntityManager. Celui-ci le fait, et
 * -- c'est tout le point -- **sans** `@Transactional` sur la classe : une transaction
 * de test masquerait le defaut exactement comme le faisait `open-in-view`.
 */
@SpringBootTest
@ActiveProfiles("test")
class CardDeckTransactionTest {

    @Autowired
    private CardService cardService;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private CardCategoryRepository cardCategoryRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    private UUID cardId;
    private UUID categoryId;
    private UUID userId;

    @AfterEach
    void removeWhatThisTestCreated() {
        if (cardId != null) cardRepository.deleteById(cardId);
        if (categoryId != null) cardCategoryRepository.deleteById(categoryId);
        if (userId != null) userRepository.deleteById(userId);
    }

    @Test
    void theDeckReadsTheCategoryOfACardEvenThoughNoTransactionIsOpen() {
        CardCategory category = new CardCategory();
        category.setName("TRANSACTION-" + UUID.randomUUID().toString().substring(0, 8));
        categoryId = cardCategoryRepository.save(category).getId();

        Card card = new Card();
        card.setName("Carte de controle transactionnel");
        card.setCategoryRef(cardCategoryRepository.findById(categoryId).orElseThrow());
        card.setEnabled(true);
        card.setContentType("image/webp");
        card.setImageObjectKey("cards/temoin.webp");
        card.setImageUrl("https://exemple.test/cards/temoin.webp");
        card.setThumbnailObjectKey("cards/temoin-mini.webp");
        card.setThumbnailUrl("https://exemple.test/cards/temoin-mini.webp");
        cardId = cardRepository.save(card).getId();

        AppUser player = new AppUser();
        String keycloakSub = "sub-" + UUID.randomUUID();
        player.setKeycloakSub(keycloakSub);
        player.setEmail(keycloakSub + "@insa-rouen.fr");
        player.setDisplayName("Joueuse temoin");
        userId = userRepository.save(player).getId();

        // La surcharge appelee par CardController.getMyDeck, et elle seule.
        assertThatCode(() -> {
            CardDeckDto deck = cardService.getDeck(
                    keycloakSub, CardCollectionType.VIRTUAL, CardGrouping.RANK, CardCollectionScope.PERSONAL);
            assertThat(deck.categories())
                    .as("la carte creee doit apparaitre dans le deck")
                    .anySatisfy(group -> assertThat(group.cards())
                            .anySatisfy(deckCard -> assertThat(deckCard.id()).isEqualTo(cardId)));
        }).doesNotThrowAnyException();
    }
}
