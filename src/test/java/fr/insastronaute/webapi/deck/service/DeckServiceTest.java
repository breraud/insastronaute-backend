package fr.insastronaute.webapi.deck.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.card.service.CollectionRewardService;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardCategory;
import fr.insastronaute.webapi.card.dto.CardDto;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.deck.domain.Deck;
import fr.insastronaute.webapi.deck.domain.DeckKind;
import fr.insastronaute.webapi.deck.domain.DropTier;
import fr.insastronaute.webapi.deck.dto.DeckCategoryImageDto;
import fr.insastronaute.webapi.deck.dto.DeckSummaryDto;
import fr.insastronaute.webapi.deck.dto.DropTierDto;
import fr.insastronaute.webapi.deck.dto.DropTierOrderUpdateRequest;
import fr.insastronaute.webapi.deck.dto.PullResultDto;
import fr.insastronaute.webapi.deck.repository.DeckRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeckServiceTest {

    @Mock
    private DeckRepository deckRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CardCollectionRepository collectionRepository;

    @Mock
    private DeckImageService imageService;

    @Mock
    private AutoChallengeTriggerPublisher autoChallengeTriggerPublisher;

    private DeckService deckService;

    @BeforeEach
    void setUp() {
        Validator validator;
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            validator = validatorFactory.getValidator();
        }
        deckService = new DeckService(
                deckRepository,
                cardRepository,
                userRepository,
                collectionRepository,
                imageService,
                new ObjectMapper(),
                validator,
                autoChallengeTriggerPublisher, mock(CollectionRewardService.class));
    }

    @Test
    void rameneUnCadrageAberrantDansLesBornes() {
        // Les valeurs viennent de deux curseurs : hors champ, elles disent une main
        // qui a glissé, pas une intention. On ramène au bord plutôt que de refuser
        // l'enregistrement du pack pour ça.
        Card card = card("Rare");
        when(cardRepository.findAllById(any())).thenReturn(List.of(card));
        when(imageService.store(any(), any())).thenReturn("decks/pack.jpg");
        when(deckRepository.save(any(Deck.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String payload = """
                {"name":"Pack","price":10,"cardsPerPull":1,
                 "frontXPercent":420,"frontZoom":99,
                 "dropTiers":[{"label":"Rare","dropChancePercent":100,"cardIds":["%s"]}]}
                """.formatted(card.getId());

        deckService.create(payload, new MockMultipartFile("image", "p.jpg", "image/jpeg", new byte[]{1}));

        ArgumentCaptor<Deck> saved = ArgumentCaptor.forClass(Deck.class);
        verify(deckRepository).save(saved.capture());
        assertThat(saved.getValue().getFrontXPercent()).isEqualTo(100.0);
        assertThat(saved.getValue().getFrontZoom()).isEqualTo(4.0);
    }

    @Test
    void cadreAuCentreQuandRienNEstPrecise() {
        // Un emballage symétrique se recadre au centre sans réglage : c'est le cas
        // courant, il ne doit rien demander à personne.
        Card card = card("Rare");
        when(cardRepository.findAllById(any())).thenReturn(List.of(card));
        when(imageService.store(any(), any())).thenReturn("decks/pack.jpg");
        when(deckRepository.save(any(Deck.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String payload = """
                {"name":"Pack","price":10,"cardsPerPull":1,
                 "dropTiers":[{"label":"Rare","dropChancePercent":100,"cardIds":["%s"]}]}
                """.formatted(card.getId());

        deckService.create(payload, new MockMultipartFile("image", "p.jpg", "image/jpeg", new byte[]{1}));

        ArgumentCaptor<Deck> saved = ArgumentCaptor.forClass(Deck.class);
        verify(deckRepository).save(saved.capture());
        assertThat(saved.getValue().getFrontXPercent()).isEqualTo(50.0);
        assertThat(saved.getValue().getFrontZoom()).isEqualTo(1.0);
    }

    @Test
    void pullIncludesGuaranteedCardsFromDropTiers() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setTokens(100L);

        Card rareCard = card("Rare");
        Card commonCard = card("Common");
        Deck deck = deck(List.of(
                guaranteedTier("Rare", rareCard),
                guaranteedTier("Common", commonCard)
        ));

        when(userRepository.findWithLockByKeycloakSub("player-sub")).thenReturn(Optional.of(user));
        when(deckRepository.findWithDropTiersById(deck.getId())).thenReturn(Optional.of(deck));
        when(collectionRepository.findByUserIdAndCardIdAndCollectionType(any(), any(), any()))
                .thenReturn(Optional.empty());

        PullResultDto result = deckService.pull(deck.getId(), "player-sub");

        assertThat(result.remainingTokens()).isEqualTo(90L);
        assertThat(result.drops())
                .extracting(drop -> drop.card().name())
                .containsExactly("Rare", "Common");
        verify(collectionRepository, times(2)).save(any());
    }

    @Test
    void reordersEveryDropTierAndReturnsTheRequestedVisualOrder() {
        DropTier common = guaranteedTier("Common", card("Common"));
        DropTier rare = guaranteedTier("Rare", card("Rare"));
        DropTier epic = guaranteedTier("Epic", card("Epic"));
        Deck deck = deck(List.of(common, rare, epic));
        when(deckRepository.findWithDropTiersById(deck.getId())).thenReturn(Optional.of(deck));

        var result = deckService.reorderDropTiers(
                deck.getId(),
                new DropTierOrderUpdateRequest(List.of(epic.getId(), rare.getId(), common.getId()))
        );

        assertThat(result.dropTiers()).extracting(DropTierDto::label)
                .containsExactly("Epic", "Rare", "Common");
        assertThat(deck.getDropTiers()).extracting(DropTier::getDisplayOrder)
                .containsExactly(2, 1, 0);
    }

    @Test
    void updateReplacesDeckFieldsAndTiersWithoutReplacingTheImage() {
        Card oldCard = card("Old card");
        Card updatedCard = card("Updated card");
        Deck deck = deck(List.of(guaranteedTier("Old", oldCard)));
        String existingImageUrl = deck.getImageUrl();
        when(deckRepository.findWithDropTiersById(deck.getId())).thenReturn(Optional.of(deck));
        when(cardRepository.findAllById(any())).thenReturn(List.of(updatedCard));
        when(deckRepository.save(deck)).thenReturn(deck);
        when(imageService.publicUrlForStoredValue(existingImageUrl)).thenReturn(existingImageUrl);

        var result = deckService.update(deck.getId(), """
                {
                  "name":" Updated starter ",
                  "price":20,
                  "cardsPerPull":1,
                  "dropTiers":[{
                    "label":"Updated",
                    "dropChancePercent":100,
                    "cardIds":["%s"]
                  }]
                }
                """.formatted(updatedCard.getId()), null);

        assertThat(result.name()).isEqualTo("Updated starter");
        assertThat(result.price()).isEqualTo(20L);
        assertThat(result.cardsPerPull()).isEqualTo(1);
        assertThat(result.imageUrl()).isEqualTo(existingImageUrl);
        assertThat(result.dropTiers()).extracting(DropTierDto::label).containsExactly("Updated");
        assertThat(result.dropTiers().getFirst().cards()).extracting(CardDto::id)
                .containsExactly(updatedCard.getId());
        verify(imageService, times(0)).store(any(), any());
    }

    @Test
    void sellingASingleCardKeepsTheSharedPurchasePipeline() {
        Card soloCard = card("Carte solo");
        Deck deck = deck(List.of(guaranteedTier("Solo", soloCard)));
        String existingImageUrl = deck.getImageUrl();
        when(deckRepository.findWithDropTiersById(deck.getId())).thenReturn(Optional.of(deck));
        when(cardRepository.findAllById(any())).thenReturn(List.of(soloCard));
        when(deckRepository.save(deck)).thenReturn(deck);
        when(imageService.publicUrlForStoredValue(existingImageUrl)).thenReturn(existingImageUrl);

        var result = deckService.update(deck.getId(), """
                {
                  "name":"Carte solo",
                  "price":40,
                  "cardsPerPull":1,
                  "kind":"CARD",
                  "dropTiers":[{
                    "dropChancePercent":100,
                    "cardIds":["%s"]
                  }]
                }
                """.formatted(soloCard.getId()), null);

        assertThat(result.kind()).isEqualTo(DeckKind.CARD);
        assertThat(result.dropTiers().getFirst().cards()).extracting(CardDto::id)
                .containsExactly(soloCard.getId());
    }

    @Test
    void aSingleCardSaleCannotHideMoreThanOneCard() {
        Card first = card("Première");
        Card second = card("Seconde");

        String payload = """
                {
                  "name":"Fausse carte solo",
                  "price":40,
                  "cardsPerPull":1,
                  "kind":"CARD",
                  "dropTiers":[{
                    "dropChancePercent":100,
                    "cardIds":["%s","%s"]
                  }]
                }
                """.formatted(first.getId(), second.getId());

        assertThatThrownBy(() -> deckService.update(UUID.randomUUID(), payload, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Une carte à l'unité doit contenir exactement une carte.");
    }

    @Test
    void aSingleCardSaleCannotSplitAcrossTiers() {
        Card first = card("Première");
        Card second = card("Seconde");

        String payload = """
                {
                  "name":"Fausse carte solo",
                  "price":40,
                  "cardsPerPull":1,
                  "kind":"CARD",
                  "dropTiers":[
                    {"dropChancePercent":50,"cardIds":["%s"]},
                    {"dropChancePercent":50,"cardIds":["%s"]}
                  ]
                }
                """.formatted(first.getId(), second.getId());

        assertThatThrownBy(() -> deckService.update(UUID.randomUUID(), payload, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Une carte à l'unité ne peut avoir qu'un seul palier.");
    }

    @Test
    void aDeckWithoutAnExplicitKindStaysAPack() {
        Card soloCard = card("Carte");
        Deck deck = deck(List.of(guaranteedTier("Solo", soloCard)));
        String existingImageUrl = deck.getImageUrl();
        when(deckRepository.findWithDropTiersById(deck.getId())).thenReturn(Optional.of(deck));
        when(cardRepository.findAllById(any())).thenReturn(List.of(soloCard));
        when(deckRepository.save(deck)).thenReturn(deck);
        when(imageService.publicUrlForStoredValue(existingImageUrl)).thenReturn(existingImageUrl);

        var result = deckService.update(deck.getId(), """
                {
                  "name":"Pack classique",
                  "price":20,
                  "cardsPerPull":1,
                  "dropTiers":[{"dropChancePercent":100,"cardIds":["%s"]}]
                }
                """.formatted(soloCard.getId()), null);

        assertThat(result.kind()).isEqualTo(DeckKind.PACK);
    }

    @Test
    void detailIncludesCategoryImagesUsedByTheDeck() {
        CardCategory rareCategory = category("Rare");
        CardCategory commonCategory = category("Common");
        Card rareCard = card("Rare card");
        rareCard.setCategoryRef(rareCategory);
        Card commonCard = card("Common card");
        commonCard.setCategoryRef(commonCategory);
        Deck deck = deck(List.of(
                guaranteedTier("Common", commonCard),
                guaranteedTier("Rare", rareCard)
        ));

        when(deckRepository.findWithDropTiersById(deck.getId())).thenReturn(Optional.of(deck));

        var result = deckService.findById(deck.getId());

        assertThat(result.categoryImages())
                .extracting(DeckCategoryImageDto::category)
                .containsExactly("Common", "Rare");
        assertThat(result.categoryImages())
                .extracting(DeckCategoryImageDto::imageUrl)
                .containsExactly(
                        "https://s3.example.com/card-categories/Common/original.jpg",
                        "https://s3.example.com/card-categories/Rare/original.jpg"
                );
    }

    @Test
    void publicDetailOmitsHiddenCategoryCards() {
        CardCategory visibleCategory = category("Visible");
        CardCategory hiddenCategory = category("Secret");
        hiddenCategory.setHidden(true);
        CardCategory gameCategory = category("Game");
        gameCategory.setGame(true);

        Card visibleCard = card("Visible card");
        visibleCard.setCategoryRef(visibleCategory);
        Card hiddenCard = card("Secret card");
        hiddenCard.setCategoryRef(hiddenCategory);
        Card gameCard = card("Game card");
        gameCard.setCategoryRef(gameCategory);
        Deck deck = deck(List.of(
                guaranteedTier("Visible", visibleCard),
                guaranteedTier("Secret", hiddenCard),
                guaranteedTier("Game", gameCard)
        ));

        when(deckRepository.findWithDropTiersById(deck.getId())).thenReturn(Optional.of(deck));

        var result = deckService.findById(deck.getId());

        assertThat(result.categoryImages())
                .extracting(DeckCategoryImageDto::category)
                .containsExactly("Visible");
        assertThat(result.dropTiers())
                .extracting(DropTierDto::label)
                .containsExactly("Visible");
        assertThat(result.dropTiers())
                .flatExtracting(DropTierDto::cards)
                .extracting(CardDto::id)
                .containsExactly(visibleCard.getId());
    }

    @Test
    void adminReorderResponseKeepsHiddenCategoryCards() {
        CardCategory hiddenCategory = category("Secret");
        hiddenCategory.setHidden(true);
        Card hiddenCard = card("Secret card");
        hiddenCard.setCategoryRef(hiddenCategory);
        DropTier hiddenTier = guaranteedTier("Secret", hiddenCard);
        Deck deck = deck(List.of(hiddenTier));
        when(deckRepository.findWithDropTiersById(deck.getId())).thenReturn(Optional.of(deck));

        var result = deckService.reorderDropTiers(
                deck.getId(),
                new DropTierOrderUpdateRequest(List.of(hiddenTier.getId()))
        );

        assertThat(result.categoryImages())
                .extracting(DeckCategoryImageDto::category)
                .containsExactly("Secret");
        assertThat(result.dropTiers())
                .flatExtracting(DropTierDto::cards)
                .extracting(CardDto::id)
                .containsExactly(hiddenCard.getId());
    }

    @Test
    void pullDoesNotDropHiddenCategoryCards() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setTokens(100L);

        CardCategory visibleCategory = category("Visible");
        CardCategory hiddenCategory = category("Secret");
        hiddenCategory.setHidden(true);
        Card visibleCard = card("Visible card");
        visibleCard.setCategoryRef(visibleCategory);
        Card hiddenCard = card("Secret card");
        hiddenCard.setCategoryRef(hiddenCategory);
        Deck deck = deck(List.of(
                guaranteedTier("Secret", hiddenCard),
                guaranteedTier("Visible", visibleCard)
        ));
        deck.setCardsPerPull(1);

        when(userRepository.findWithLockByKeycloakSub("player-sub")).thenReturn(Optional.of(user));
        when(deckRepository.findWithDropTiersById(deck.getId())).thenReturn(Optional.of(deck));
        when(collectionRepository.findByUserIdAndCardIdAndCollectionType(any(), any(), any()))
                .thenReturn(Optional.empty());

        PullResultDto result = deckService.pull(deck.getId(), "player-sub");

        assertThat(result.drops())
                .extracting(drop -> drop.card().id())
                .containsExactly(visibleCard.getId());
    }

    @Test
    void deckResponsesResolveLegacyPublicImagePathsToS3Urls() {
        Deck deck = deck(List.of(guaranteedTier("Common", card("Common"))));
        deck.setImageUrl("/public/decks/" + deck.getId() + "/image.png");
        String s3Url = "https://s3.example.com/insastronaute-public/decks/" + deck.getId() + "/image.png";

        when(deckRepository.findAll()).thenReturn(List.of(deck));
        when(deckRepository.findWithDropTiersById(deck.getId())).thenReturn(Optional.of(deck));
        when(imageService.publicUrlForStoredValue(deck.getImageUrl())).thenReturn(s3Url);

        List<DeckSummaryDto> summaries = deckService.findAll();
        var detail = deckService.findById(deck.getId());

        assertThat(summaries).extracting(DeckSummaryDto::imageUrl).containsExactly(s3Url);
        assertThat(detail.imageUrl()).isEqualTo(s3Url);
    }

    @Test
    void rejectsADropTierOrderThatDoesNotContainEveryTier() {
        DropTier common = guaranteedTier("Common", card("Common"));
        DropTier rare = guaranteedTier("Rare", card("Rare"));
        Deck deck = deck(List.of(common, rare));
        when(deckRepository.findWithDropTiersById(deck.getId())).thenReturn(Optional.of(deck));

        assertThatThrownBy(() -> deckService.reorderDropTiers(
                deck.getId(),
                new DropTierOrderUpdateRequest(List.of(rare.getId()))
        )).hasMessage("La liste doit contenir exactement les paliers de ce pack.");
    }

    @Test
    void deletingAPackRemovesItAndItsVisualsWithoutTouchingCollections() {
        Deck deck = deck(List.of(guaranteedTier("Rare", card("Rare"))));
        when(deckRepository.findWithDropTiersById(deck.getId())).thenReturn(Optional.of(deck));

        deckService.delete(deck.getId());

        verify(deckRepository).delete(deck);
        verify(imageService).deleteAll(deck.getId());
        // Une carte tirée ne garde pas trace de son pack : rien à retirer.
        verify(collectionRepository, never()).delete(any());
    }

    @Test
    void deletingAPackThatIsNotThereIsRefused() {
        UUID missing = UUID.randomUUID();
        when(deckRepository.findWithDropTiersById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deckService.delete(missing))
                .isInstanceOf(fr.insastronaute.webapi.common.web.NotFoundException.class);

        verify(deckRepository, never()).delete(any());
    }

    private Deck deck(List<DropTier> dropTiers) {
        Deck deck = new Deck();
        deck.setId(UUID.randomUUID());
        deck.setName("Starter");
        deck.setPrice(10L);
        deck.setCardsPerPull(2);
        deck.setImageUrl("https://s3.example.com/decks/starter.jpg");
        deck.setDropTiers(dropTiers);
        return deck;
    }

    @Test
    void theSamePercentageIsCommonInAWidePackAndRareInASingleCardOne() {
        // C'est tout l'objet du calcul : 10 % ne veut rien dire tant qu'on ignore
        // combien de cartes le pack tire.
        double wide = DropTierRules.pullProbability(10, 10, null);
        double narrow = DropTierRules.pullProbability(10, 1, null);

        assertThat(DropTierRules.rarityOf(DropTierRules.expectedPullsForOne(wide)))
                .isEqualTo(fr.insastronaute.webapi.deck.domain.DropTierRarity.COMMON);
        assertThat(DropTierRules.rarityOf(DropTierRules.expectedPullsForOne(narrow)))
                .isEqualTo(fr.insastronaute.webapi.deck.domain.DropTierRarity.RARE);
    }

    @Test
    void aGuaranteedTierIsNeverDramatised() {
        double probability = DropTierRules.pullProbability(3, 5, 1);

        assertThat(probability).isEqualTo(1.0);
        assertThat(DropTierRules.rarityOf(DropTierRules.expectedPullsForOne(probability)))
                .isEqualTo(fr.insastronaute.webapi.deck.domain.DropTierRarity.COMMON);
    }

    @Test
    void aVanishinglyRareTierIsLegendary() {
        double probability = DropTierRules.pullProbability(0.5, 1, null);

        assertThat(DropTierRules.rarityOf(DropTierRules.expectedPullsForOne(probability)))
                .isEqualTo(fr.insastronaute.webapi.deck.domain.DropTierRarity.LEGENDARY);
    }

    @Test
    void guaranteedMinimumsShrinkWhatTheOtherTiersCanHopeFor() {
        // Trois places sur cinq déjà réservées : les paliers ouverts se disputent
        // les deux restantes, et sont donc plus rares qu'il n'y paraît.
        assertThat(DropTierRules.freeSlots(5, 3)).isEqualTo(2);
        // Un pack entièrement garanti garde une place théorique plutôt que zéro,
        // faute de quoi la probabilité serait indéfinie.
        assertThat(DropTierRules.freeSlots(5, 5)).isEqualTo(1);
    }

    @Test
    void aPulledDropCarriesTheRarityThatDrivesTheAnimation() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setTokens(100L);
        Deck deck = deck(List.of(
                guaranteedTier("Rare", card("Rare")),
                guaranteedTier("Common", card("Common"))
        ));

        when(userRepository.findWithLockByKeycloakSub("player-sub")).thenReturn(Optional.of(user));
        when(deckRepository.findWithDropTiersById(deck.getId())).thenReturn(Optional.of(deck));
        when(collectionRepository.findByUserIdAndCardIdAndCollectionType(any(), any(), any()))
                .thenReturn(Optional.empty());

        PullResultDto result = deckService.pull(deck.getId(), "player-sub");

        assertThat(result.drops()).allSatisfy(drop -> assertThat(drop.rarity()).isNotNull());
    }

    private DropTier guaranteedTier(String label, Card card) {
        DropTier tier = new DropTier();
        tier.setId(UUID.randomUUID());
        tier.setLabel(label);
        tier.setDropChancePercent(BigDecimal.valueOf(50));
        tier.setMinCardsPerPull(1);
        tier.setMaxCardsPerPull(1);
        tier.setCards(List.of(card));
        return tier;
    }

    private Card card(String name) {
        Card card = new Card();
        card.setId(UUID.randomUUID());
        card.setName(name);
        card.setCategory("Default");
        card.setEnabled(true);
        card.setDefaultCard(false);
        card.setDisplayOrder(0);
        card.setImageObjectKey("cards/" + name + "/original.jpg");
        card.setThumbnailObjectKey("cards/" + name + "/thumbnail.jpg");
        card.setImageUrl("https://s3.example.com/cards/" + name + "/original.jpg");
        card.setThumbnailUrl("https://s3.example.com/cards/" + name + "/thumbnail.jpg");
        card.setContentType("image/jpeg");
        return card;
    }

    private CardCategory category(String name) {
        CardCategory category = new CardCategory();
        category.setId(UUID.randomUUID());
        category.setName(name);
        category.setImageUrl("https://s3.example.com/card-categories/" + name + "/original.jpg");
        category.setThumbnailUrl("https://s3.example.com/card-categories/" + name + "/thumbnail.jpg");
        return category;
    }
}
