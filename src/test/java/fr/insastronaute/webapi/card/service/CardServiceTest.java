package fr.insastronaute.webapi.card.service;

import fr.insastronaute.webapi.card.service.CollectionPointsCalculator;
import fr.insastronaute.webapi.card.service.CollectionRewardService;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardCategory;
import fr.insastronaute.webapi.card.domain.CardCollection;
import fr.insastronaute.webapi.card.domain.CardCollectionSettings;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.domain.CardGrouping;
import fr.insastronaute.webapi.card.domain.CardCollectionScope;
import fr.insastronaute.webapi.cardteam.domain.CardTeam;
import fr.insastronaute.webapi.cardteam.domain.CardTeamMember;
import fr.insastronaute.webapi.cardteam.repository.CardTeamMemberRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamRepository;
import fr.insastronaute.webapi.card.domain.CardType;
import fr.insastronaute.webapi.card.goofy.GoofyCardService;
import fr.insastronaute.webapi.card.dto.CardCollectionGrantRequest;
import fr.insastronaute.webapi.card.dto.CardDeckCardDto;
import fr.insastronaute.webapi.card.dto.CardDeckCategoryDto;
import fr.insastronaute.webapi.card.dto.CardDeckDto;
import fr.insastronaute.webapi.card.dto.CardDto;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.repository.CardCategoryRepository;
import fr.insastronaute.webapi.card.repository.CardInstanceRepository;
import fr.insastronaute.webapi.card.print.repository.CardPrintRunSlotRepository;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.deck.repository.DeckRepository;
import fr.insastronaute.webapi.deck.repository.DropTierTemplateRepository;
import fr.insastronaute.webapi.deck.domain.Deck;
import fr.insastronaute.webapi.deck.domain.DropTier;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.card.repository.CardTypeRepository;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CardCategoryRepository categoryRepository;

    @Mock
    private CardTypeRepository typeRepository;

    @Mock
    private CardCollectionRepository collectionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CardImageService imageService;

    @Mock
    private CardTeamMemberRepository teamMemberRepository;

    @Mock
    private CardTeamRepository teamRepository;

    @Mock
    private AutoChallengeTriggerPublisher autoChallengeTriggerPublisher;

    @Mock
    private CollectionRewardService collectionRewardService;

    @Mock
    private CollectionPointsCalculator collectionPointsCalculator;

    @Mock
    private AdminActionJournal journal;

    // Injecté par type : sans ce mock, le champ reste nul et getDeck casse.
    @Mock
    private CollectionTestModeService collectionTestMode;

    @Mock
    private GoofyCardService goofyCardService;

    @Mock
    private CardInstanceRepository cardInstanceRepository;

    @Mock
    private CardPrintRunSlotRepository printRunSlotRepository;

    @Mock
    private DeckRepository deckRepository;

    @Mock
    private DropTierTemplateRepository dropTierTemplateRepository;

    @InjectMocks
    private CardService cardService;

    @Test
    void loadsATeamsAggregatedMainPhysicalCollectionByTeamId() {
        AppUser alice = new AppUser();
        alice.setId(UUID.randomUUID());
        AppUser bob = new AppUser();
        bob.setId(UUID.randomUUID());
        CardTeam team = new CardTeam();
        team.setId(UUID.randomUUID());
        team.setName("Nova");
        CardTeamMember aliceMembership = teamMember(team, alice);
        CardTeamMember bobMembership = teamMember(team, bob);

        CardCategory mainCategory = category(UUID.randomUUID(), "Principale");
        Card main = card(UUID.randomUUID(), "Fusee", "Principale", false, 1, null, null);
        main.setCategoryRef(mainCategory);
        main.setFirstAcquisitionPoints(100);
        main.setDuplicatePoints(25);
        CardCategory extensionCategory = category(UUID.randomUUID(), "Magellan");
        extensionCategory.setExtension(true);
        Card extension = card(UUID.randomUUID(), "Etape Magellan", "Magellan", false, 2, null, null);
        extension.setCategoryRef(extensionCategory);

        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamIdOrderByJoinedAt(team.getId()))
                .thenReturn(List.of(aliceMembership, bobMembership));
        when(collectionRepository.findByUserIdsAndCollectionType(
                List.of(alice.getId(), bob.getId()), CardCollectionType.PHYSICAL
        )).thenReturn(List.of(
                collection(alice, main, 2),
                collection(bob, main, 1),
                collection(alice, extension, 1)
        ));
        when(cardRepository.findByEnabledTrue()).thenReturn(List.of(main, extension));
        CardCollectionSettings settings = new CardCollectionSettings();
        settings.setPhysicalFirstAcquisitionMultiplier(3);
        settings.setPhysicalDuplicatesEnabled(false);
        when(collectionPointsCalculator.settings()).thenReturn(settings);
        when(collectionPointsCalculator.points(main, CardCollectionType.PHYSICAL, 3, settings))
                .thenReturn(300L);

        CardDeckDto deck = cardService.getTeamDeck(team.getId());

        assertThat(deck.ownedCount()).isEqualTo(1);
        assertThat(deck.totalCount()).isEqualTo(1);
        assertThat(deck.teamScore()).isEqualTo(300);
        assertThat(deck.team().name()).isEqualTo("Nova");
        assertThat(deck.categories()).flatExtracting(CardDeckCategoryDto::cards)
                .singleElement().satisfies(card -> {
                    assertThat(card.quantity()).isEqualTo(3);
                    assertThat(card.collectionPoints()).isEqualTo(300);
                });
    }

    @Test
    void rejectsAnUnknownTeamId() {
        UUID teamId = UUID.randomUUID();
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.getTeamDeck(teamId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Equipe introuvable.");
    }

    @Test
    void aggregatesOnlyMainPhysicalCardsAndScoresThemAtTeamScale() {
        AppUser alice = new AppUser();
        alice.setId(UUID.randomUUID());
        AppUser bob = new AppUser();
        bob.setId(UUID.randomUUID());
        CardTeam team = new CardTeam();
        team.setId(UUID.randomUUID());
        team.setName("Nova");
        CardTeamMember aliceMembership = teamMember(team, alice);
        CardTeamMember bobMembership = teamMember(team, bob);

        CardCategory mainCategory = category(UUID.randomUUID(), "Principale");
        Card main = card(UUID.randomUUID(), "Fusee", "Principale", false, 1, null, null);
        main.setCategoryRef(mainCategory);
        main.setFirstAcquisitionPoints(100);
        main.setDuplicatePoints(25);
        CardCategory extensionCategory = category(UUID.randomUUID(), "Magellan");
        extensionCategory.setExtension(true);
        Card extension = card(UUID.randomUUID(), "Etape Magellan", "Magellan", false, 2, null, null);
        extension.setCategoryRef(extensionCategory);

        CardCollection aliceMain = collection(alice, main, 2);
        CardCollection bobMain = collection(bob, main, 1);
        CardCollection aliceExtension = collection(alice, extension, 1);
        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(alice));
        when(teamMemberRepository.findByUserId(alice.getId())).thenReturn(Optional.of(aliceMembership));
        when(teamMemberRepository.findByTeamIdOrderByJoinedAt(team.getId())).thenReturn(List.of(aliceMembership, bobMembership));
        when(collectionRepository.findByUserIdsAndCollectionType(
                List.of(alice.getId(), bob.getId()), CardCollectionType.PHYSICAL
        )).thenReturn(List.of(aliceMain, bobMain, aliceExtension));
        when(cardRepository.findByEnabledTrue()).thenReturn(List.of(main, extension));
        CardCollectionSettings settings = new CardCollectionSettings();
        when(collectionPointsCalculator.settings()).thenReturn(settings);
        when(collectionPointsCalculator.points(main, CardCollectionType.PHYSICAL, 3, settings))
                .thenReturn(200L);

        CardDeckDto deck = cardService.getDeck(
                "player-sub", CardCollectionType.PHYSICAL, CardGrouping.RANK, CardCollectionScope.TEAM
        );

        assertThat(deck.ownedCount()).isEqualTo(1);
        assertThat(deck.totalCount()).isEqualTo(1);
        assertThat(deck.teamScore()).isEqualTo(200);
        assertThat(deck.team().name()).isEqualTo("Nova");
        assertThat(deck.categories()).flatExtracting(CardDeckCategoryDto::cards)
                .singleElement().satisfies(card -> assertThat(card.quantity()).isEqualTo(3));
    }

    /**
     * Sans equipe, la collection d'equipe est celle du joueur.
     *
     * <p>L'onglet est offert a tout le monde, et il repondait « Vous devez rejoindre une
     * equipe » a qui n'en a pas — un ecran d'erreur rouge la ou il n'y a rien d'anormal.
     * Un joueur seul est une equipe d'un, et sa collection commune est la sienne : c'est
     * la meme reponse qu'il rejoigne quelqu'un ou non, aux regles d'equipe pres.</p>
     */
    @Test
    void showsThePlayerOwnCollectionWhenNoTeamHasBeenJoined() {
        AppUser solo = new AppUser();
        solo.setId(UUID.randomUUID());
        CardCategory mainCategory = category(UUID.randomUUID(), "Principale");
        Card main = card(UUID.randomUUID(), "Fusee", "Principale", false, 1, null, null);
        main.setCategoryRef(mainCategory);
        main.setFirstAcquisitionPoints(100);
        main.setDuplicatePoints(25);
        when(userRepository.findByKeycloakSub("solo-sub")).thenReturn(Optional.of(solo));
        when(teamMemberRepository.findByUserId(solo.getId())).thenReturn(Optional.empty());
        when(collectionRepository.findByUserIdsAndCollectionType(List.of(solo.getId()), CardCollectionType.PHYSICAL))
                .thenReturn(List.of(collection(solo, main, 2)));
        when(cardRepository.findByEnabledTrue()).thenReturn(List.of(main));

        CardDeckDto deck = cardService.getDeck(
                "solo-sub", CardCollectionType.PHYSICAL, CardGrouping.RANK, CardCollectionScope.TEAM
        );

        assertThat(deck.ownedCount()).isEqualTo(1);
        assertThat(deck.categories()).flatExtracting(CardDeckCategoryDto::cards)
                .singleElement().satisfies(card -> assertThat(card.quantity()).isEqualTo(2));
        // Aucune equipe n'est nommee, et aucun score d'equipe n'est annonce : il n'y en a
        // pas. L'ecran ne montre donc pas un en-tete d'equipe qui n'existe pas.
        assertThat(deck.team()).isNull();
        assertThat(deck.teamScore()).isNull();
    }

    @Test
    void rejectsTeamScopeForVirtualCards() {
        assertThatThrownBy(() -> cardService.getDeck(
                "player-sub", CardCollectionType.VIRTUAL, CardGrouping.RANK, CardCollectionScope.TEAM
        )).isInstanceOf(BadRequestException.class);
    }

    @Test
    void createsCardWithOptionalType() {
        UUID categoryId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        CardCategory category = category(categoryId, "Commun");
        CardType type = new CardType();
        type.setId(typeId);
        type.setName("Personnage");
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(typeRepository.findById(typeId)).thenReturn(Optional.of(type));
        when(imageService.store(any(), any())).thenReturn(new CardImageService.StoredCardImages(
                "image", "thumbnail", "image-url", "thumbnail-url", "image/jpeg"));
        when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardDto result = cardService.create("Carte", categoryId, null, typeId, true, false, 0, imageFile());

        assertThat(result.typeId()).isEqualTo(typeId);
        assertThat(result.type()).isEqualTo("Personnage");
    }

    @Test
    void groupsPlayerDeckByOptionalType() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        CardType type = new CardType();
        type.setId(UUID.randomUUID());
        type.setName("Personnage");
        Card typed = card(UUID.randomUUID(), "Avec type", "Rare", false, 1, null, null);
        typed.setTypeRef(type);
        Card untyped = card(UUID.randomUUID(), "Sans", "Rare", false, 2, null, null);
        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(user));
        when(collectionRepository.findByUserIdAndCollectionType(user.getId(), CardCollectionType.VIRTUAL))
                .thenReturn(List.of());
        when(cardRepository.findByEnabledTrue()).thenReturn(List.of(typed, untyped));

        CardDeckDto deck = cardService.getDeck("player-sub", CardCollectionType.VIRTUAL, CardGrouping.TYPE);

        assertThat(deck.categories()).extracting(CardDeckCategoryDto::category)
                .containsExactly("Personnage", "Sans type");
    }

    @Test
    void listsHiddenCategoryCardsForStsArtwork() {
        CardCategory publicCategory = category(UUID.randomUUID(), "Public");
        CardCategory gameCategory = category(UUID.randomUUID(), "Game");
        gameCategory.setGame(true);
        CardCategory hiddenCategory = category(UUID.randomUUID(), "Hidden");
        hiddenCategory.setHidden(true);

        Card publicCard = card(UUID.randomUUID(), "Public card", "Public", false, 1, null, null);
        publicCard.setCategoryRef(publicCategory);
        Card gameCard = card(UUID.randomUUID(), "Game card", "Game", false, 1, null, null);
        gameCard.setCategoryRef(gameCategory);
        Card hiddenCard = card(UUID.randomUUID(), "Hidden card", "Hidden", false, 1, null, null);
        hiddenCard.setCategoryRef(hiddenCategory);

        when(cardRepository.findAll()).thenReturn(List.of(hiddenCard, gameCard, publicCard));

        assertThat(cardService.findAllForSts())
                .extracting(CardDto::name)
                .containsExactly("Game card", "Hidden card", "Public card");
    }

    /**
     * Une catégorie retirée garde son nom pour qui demande « d'où vient cette carte ».
     *
     * <p>« Ancienne collection » est cachée parce qu'elle n'est plus obtenable, et c'est
     * justement ce que {@code runEndUnlockableCategories} désigne. Lue à travers le deck, qui
     * écarte les catégories cachées et les cartes désactivées, sa carte revenait sans catégorie :
     * exemptée de rien, elle n'était jamais rendue à la fin d'une run.
     */
    @Test
    void namesTheCategoryOfCardsInHiddenCategoriesAndOfDisabledCards() {
        CardCategory retired = category(UUID.randomUUID(), "Ancienne collection");
        retired.setHidden(true);
        CardCategory broken = category(UUID.randomUUID(), "HS");
        broken.setHidden(true);

        Card retiredCard = card(UUID.randomUUID(), "Vieux code", "Ancienne collection", false, 1, null, null);
        retiredCard.setCategoryRef(retired);
        Card disabledCard = card(UUID.randomUUID(), "Carte cassée", "HS", false, 1, null, null);
        disabledCard.setCategoryRef(broken);
        disabledCard.setEnabled(false);

        when(cardRepository.findAll()).thenReturn(List.of(retiredCard, disabledCard));

        assertThat(cardService.categoryNamesByCardName())
                .containsEntry("Vieux code", "Ancienne collection")
                .containsEntry("carte cassée", "HS");
    }

    @Test
    void listsCardOverridesAndEffectiveInheritedCollectionPoints() {
        CardCategory category = category(UUID.randomUUID(), "Rare");
        category.setFirstAcquisitionPoints(120);
        category.setDuplicatePoints(20);
        Card card = card(UUID.randomUUID(), "Insamourai", "Rare", false, 1, null, null);
        card.setCategoryRef(category);
        card.setDuplicatePoints(0);
        when(cardRepository.findAll()).thenReturn(List.of(card));

        CardDto result = cardService.findAll().getFirst();

        assertThat(result.firstAcquisitionPoints()).isNull();
        assertThat(result.duplicatePoints()).isZero();
        assertThat(result.effectiveFirstAcquisitionPoints()).isEqualTo(120);
        assertThat(result.effectiveDuplicatePoints()).isZero();
    }

    @Test
    void excludesGameCategoriesFromPlayerDeck() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub("player-sub");
        CardCategory gameCategory = category(UUID.randomUUID(), "Game");
        gameCategory.setGame(true);
        Card gameCard = card(UUID.randomUUID(), "Game card", "Game", false, 1, null, null);
        gameCard.setCategoryRef(gameCategory);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(user));
        when(collectionRepository.findByUserIdAndCollectionType(user.getId(), CardCollectionType.VIRTUAL))
                .thenReturn(List.of());
        when(cardRepository.findByEnabledTrue()).thenReturn(List.of(gameCard));

        assertThat(cardService.getDeck("player-sub", CardCollectionType.VIRTUAL).categories()).isEmpty();
    }

    @Test
    void getsConfiguredCardCover() {
        Card defaultCard = card(
                Card.DEFAULT_CARD_ID,
                "Card cover",
                "COVER",
                true,
                0,
                "https://s3.example.com/cards/default/original.jpg",
                "https://s3.example.com/cards/default/thumbnail.jpg"
        );
        when(cardRepository.findByDefaultCardTrue()).thenReturn(Optional.of(defaultCard));

        CardDto cover = cardService.getCover();

        assertThat(cover.id()).isEqualTo(Card.DEFAULT_CARD_ID);
        assertThat(cover.imageUrl()).isEqualTo("https://s3.example.com/cards/default/original.jpg");
    }

    @Test
    void rejectsMissingCardCover() {
        when(cardRepository.findByDefaultCardTrue()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.getCover())
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Dos de carte introuvable.");
    }

    @Test
    void createsCardWithImageMetadataBeforeSaving() {
        UUID categoryId = UUID.randomUUID();
        CardCategory category = new CardCategory();
        category.setId(categoryId);
        category.setName("Commun");

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(imageService.store(any(), any())).thenReturn(new CardImageService.StoredCardImages(
                "cards/card/original.jpg",
                "cards/card/thumbnail.jpg",
                "https://s3.example.com/cards/card/original.jpg",
                "https://s3.example.com/cards/card/thumbnail.jpg",
                "image/jpeg"
        ));
        when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardDto card = cardService.create("Insamourai", categoryId, null, true, false, 0, imageFile());

        ArgumentCaptor<UUID> cardIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        verify(imageService).store(cardIdCaptor.capture(), any());
        verify(cardRepository).save(cardCaptor.capture());

        Card savedCard = cardCaptor.getValue();
        assertThat(savedCard.getId()).isEqualTo(cardIdCaptor.getValue());
        assertThat(savedCard.getImageObjectKey()).isEqualTo("cards/card/original.jpg");
        assertThat(savedCard.getThumbnailObjectKey()).isEqualTo("cards/card/thumbnail.jpg");
        assertThat(savedCard.getImageUrl()).isEqualTo("https://s3.example.com/cards/card/original.jpg");
        assertThat(savedCard.getThumbnailUrl()).isEqualTo("https://s3.example.com/cards/card/thumbnail.jpg");
        assertThat(savedCard.getContentType()).isEqualTo("image/jpeg");
        assertThat(card.name()).isEqualTo("Insamourai");
    }

    @Test
    void updatesCoverWithFixedId() {
        when(cardRepository.existsByDefaultCardTrue()).thenReturn(false);
        when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(imageService.store(any(), any())).thenReturn(new CardImageService.StoredCardImages(
                "cards/default/original.jpg",
                "cards/default/thumbnail.jpg",
                "https://s3.example.com/cards/default/original.jpg",
                "https://s3.example.com/cards/default/thumbnail.jpg",
                "image/jpeg"
        ));

        CardDto card = cardService.updateCover(imageFile());

        assertThat(card.id()).isEqualTo(Card.DEFAULT_CARD_ID);
        assertThat(card.defaultCard()).isTrue();
        assertThat(card.enabled()).isTrue();
    }

    @Test
    void keepsPreviousImagesWhenImageReplacementTransactionRollsBack() {
        UUID cardId = UUID.randomUUID();
        Card card = card(
                cardId,
                "Amir",
                "Ancienne collection",
                false,
                1,
                "https://s3.example.com/cards/old/original.jpg",
                "https://s3.example.com/cards/old/thumbnail.jpg"
        );
        String oldImageKey = card.getImageObjectKey();
        String oldThumbnailKey = card.getThumbnailObjectKey();
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(imageService.store(org.mockito.ArgumentMatchers.eq(cardId), any())).thenReturn(new CardImageService.StoredCardImages(
                "cards/new/original.jpg",
                "cards/new/thumbnail.jpg",
                "https://s3.example.com/cards/new/original.jpg",
                "https://s3.example.com/cards/new/thumbnail.jpg",
                "image/jpeg"
        ));
        when(cardRepository.save(card)).thenReturn(card);

        TransactionSynchronizationManager.initSynchronization();
        try {
            cardService.updateImage(cardId, imageFile());

            verify(imageService, never()).deleteQuietly(oldImageKey);
            verify(imageService, never()).deleteQuietly(oldThumbnailKey);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCompletion(
                            TransactionSynchronization.STATUS_ROLLED_BACK
                    ));
            verify(imageService, never()).deleteQuietly(oldImageKey);
            verify(imageService, never()).deleteQuietly(oldThumbnailKey);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deletesPreviousImagesAfterImageReplacementTransactionCommits() {
        UUID cardId = UUID.randomUUID();
        Card card = card(
                cardId,
                "Amir",
                "Ancienne collection",
                false,
                1,
                "https://s3.example.com/cards/old/original.jpg",
                "https://s3.example.com/cards/old/thumbnail.jpg"
        );
        String oldImageKey = card.getImageObjectKey();
        String oldThumbnailKey = card.getThumbnailObjectKey();
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(imageService.store(org.mockito.ArgumentMatchers.eq(cardId), any())).thenReturn(new CardImageService.StoredCardImages(
                "cards/new/original.jpg",
                "cards/new/thumbnail.jpg",
                "https://s3.example.com/cards/new/original.jpg",
                "https://s3.example.com/cards/new/thumbnail.jpg",
                "image/jpeg"
        ));
        when(cardRepository.save(card)).thenReturn(card);

        TransactionSynchronizationManager.initSynchronization();
        try {
            cardService.updateImage(cardId, imageFile());

            verify(imageService, never()).deleteQuietly(oldImageKey);
            verify(imageService, never()).deleteQuietly(oldThumbnailKey);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(imageService).deleteQuietly(oldImageKey);
            verify(imageService).deleteQuietly(oldThumbnailKey);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deletesCoverAndStoredImages() {
        Card defaultCard = card(
                Card.DEFAULT_CARD_ID,
                "Dos de carte",
                "DEFAULT",
                true,
                0,
                "https://s3.example.com/cards/default/original.jpg",
                "https://s3.example.com/cards/default/thumbnail.jpg"
        );
        defaultCard.setImageObjectKey("cards/default/original.jpg");
        defaultCard.setThumbnailObjectKey("cards/default/thumbnail.jpg");

        when(cardRepository.findById(Card.DEFAULT_CARD_ID)).thenReturn(Optional.of(defaultCard));

        cardService.deleteCover();

        verify(cardRepository).delete(defaultCard);
        verify(imageService).deleteQuietly("cards/default/original.jpg");
        verify(imageService).deleteQuietly("cards/default/thumbnail.jpg");
    }

    @Test
    void deletesAllCardImagesOnlyAfterCardDeletionCommits() {
        UUID cardId = UUID.randomUUID();
        Card card = card(cardId, "Carte", "Rare", false, 1, "image-url", "thumbnail-url");
        card.setTextlessImageObjectKey("cards/card/textless.jpg");
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));

        TransactionSynchronizationManager.initSynchronization();
        try {
            cardService.delete(cardId);

            verify(imageService, never()).deleteQuietly(card.getImageObjectKey());
            verify(imageService, never()).deleteQuietly(card.getThumbnailObjectKey());
            verify(imageService, never()).deleteQuietly(card.getTextlessImageObjectKey());
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(imageService).deleteQuietly(card.getImageObjectKey());
            verify(imageService).deleteQuietly(card.getThumbnailObjectKey());
            verify(imageService).deleteQuietly(card.getTextlessImageObjectKey());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void addsAndRemovesVirtualCardsFromAUserCatalogue() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        UUID cardId = UUID.randomUUID();
        Card card = card(cardId, "Carte", "Rare", false, 1, null, null);
        CardCollection collection = new CardCollection();
        collection.setUser(user);
        collection.setCard(card);
        collection.setCollectionType(CardCollectionType.VIRTUAL);
        collection.setQuantity(3);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(collectionRepository.findByUserIdAndCardIdAndCollectionType(user.getId(), cardId, CardCollectionType.VIRTUAL))
                .thenReturn(Optional.of(collection));

        cardService.addVirtualCards(user.getId(), cardId, 2);
        cardService.removeVirtualCards(user.getId(), cardId, 4);

        assertThat(collection.getQuantity()).isEqualTo(1);
        verify(collectionRepository, org.mockito.Mockito.times(2)).save(collection);
    }

    @Test
    void hidesUndiscoveredCardsBehindDefaultImageAndPlaceholders() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub("player-sub");
        user.setEmail("player@example.com");

        Card defaultCard = card(
                Card.DEFAULT_CARD_ID,
                "Dos de carte",
                "DEFAULT",
                true,
                0,
                "https://s3.example.com/cards/default/original.jpg",
                "https://s3.example.com/cards/default/thumbnail.jpg"
        );
        Card lockedCard = card(
                UUID.randomUUID(),
                "Carte cachee",
                "Rare",
                false,
                1,
                "https://s3.example.com/cards/locked/original.jpg",
                "https://s3.example.com/cards/locked/thumbnail.jpg"
        );

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(user));
        when(collectionRepository.findByUserIdAndCollectionType(user.getId(), CardCollectionType.VIRTUAL))
                .thenReturn(List.of());
        when(cardRepository.findByEnabledTrue()).thenReturn(List.of(defaultCard, lockedCard));

        CardDeckDto deck = cardService.getDeck("player-sub", CardCollectionType.VIRTUAL);

        assertThat(deck.ownedCount()).isZero();
        assertThat(deck.totalCount()).isEqualTo(1);
        assertThat(deck.categories())
                .flatExtracting(CardDeckCategoryDto::cards)
                .filteredOn(card -> card.id().equals(lockedCard.getId()))
                .singleElement()
                .satisfies(card -> {
                    assertThat(card.quantity()).isZero();
                    assertThat(card.name()).isEqualTo("???");
                    assertThat(card.category()).isEqualTo("???");
                    assertThat(card.imageUrl()).isEqualTo(defaultCard.getImageUrl());
                    assertThat(card.thumbnailUrl()).isEqualTo(defaultCard.getThumbnailUrl());
                    assertThat(card.firstAcquisitionPoints()).isNull();
                    assertThat(card.duplicatePoints()).isNull();
                });
    }

    @Test
    void hidesConfiguredGoofyCardUntilItIsClaimed() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub("player-sub");

        Card regularCard = card(UUID.randomUUID(), "Carte publique", "Rare", false, 1, null, null);
        Card goofyCard = card(UUID.randomUUID(), "Carte goofy", "Rare", false, 2, null, null);
        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(user));
        when(collectionRepository.findByUserIdAndCollectionType(user.getId(), CardCollectionType.PHYSICAL))
                .thenReturn(List.of());
        when(cardRepository.findByEnabledTrue()).thenReturn(List.of(regularCard, goofyCard));
        when(goofyCardService.configuredCardId()).thenReturn(Optional.of(goofyCard.getId()));

        CardDeckDto hiddenDeck = cardService.getDeck("player-sub", CardCollectionType.PHYSICAL);

        assertThat(hiddenDeck.totalCount()).isEqualTo(1);
        assertThat(hiddenDeck.categories()).flatExtracting(CardDeckCategoryDto::cards)
                .extracting(CardDeckCardDto::id)
                .containsExactly(regularCard.getId());

        when(collectionRepository.findByUserIdAndCollectionType(user.getId(), CardCollectionType.PHYSICAL))
                .thenReturn(List.of(collection(user, goofyCard, 1)));

        CardDeckDto claimedDeck = cardService.getDeck("player-sub", CardCollectionType.PHYSICAL);

        // Obtenue, elle s'affiche : c'est une surprise, pas un secret éternel.
        assertThat(claimedDeck.categories()).flatExtracting(CardDeckCategoryDto::cards)
                .extracting(CardDeckCardDto::id)
                .containsExactlyInAnyOrder(regularCard.getId(), goofyCard.getId());
        // Mais elle reste hors collection : le total ne bouge pas, et le joueur ne
        // possède toujours aucune des cartes qui comptent. Sans quoi son compteur
        // dirait autre chose que ce qu'un défi de complétion mesure.
        assertThat(claimedDeck.totalCount()).isEqualTo(1);
        assertThat(claimedDeck.ownedCount()).isEqualTo(0);
        assertThat(claimedDeck.categories())
                .extracting(CardDeckCategoryDto::totalCount)
                .containsExactly(1L);
    }

    @Test
    void theBonusCardIsWorthNoCollectionPoints() {
        // Hors collection veut dire hors score : la carte s'affiche sans points, et
        // n'ajoute rien au total d'une équipe.
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub("player-sub");

        Card goofyCard = card(UUID.randomUUID(), "Carte goofy", "Rare", false, 1, null, null);
        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(user));
        when(collectionRepository.findByUserIdAndCollectionType(user.getId(), CardCollectionType.PHYSICAL))
                .thenReturn(List.of(collection(user, goofyCard, 1)));
        when(cardRepository.findByEnabledTrue()).thenReturn(List.of(goofyCard));
        when(goofyCardService.configuredCardId()).thenReturn(Optional.of(goofyCard.getId()));

        CardDeckDto deck = cardService.getDeck("player-sub", CardCollectionType.PHYSICAL);

        assertThat(deck.categories()).flatExtracting(CardDeckCategoryDto::cards)
                .extracting(CardDeckCardDto::collectionPoints)
                .containsExactly((Long) null);
    }

    @Test
    void hidesUndiscoveredCardImageWhenCoverIsMissing() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub("player-sub");
        user.setEmail("player@example.com");
        Card lockedCard = card(
                UUID.randomUUID(),
                "Carte cachee",
                "Rare",
                false,
                1,
                "https://s3.example.com/cards/locked/original.jpg",
                "https://s3.example.com/cards/locked/thumbnail.jpg"
        );

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(user));
        when(collectionRepository.findByUserIdAndCollectionType(user.getId(), CardCollectionType.VIRTUAL))
                .thenReturn(List.of());
        when(cardRepository.findByEnabledTrue()).thenReturn(List.of(lockedCard));

        CardDeckDto deck = cardService.getDeck("player-sub", CardCollectionType.VIRTUAL);

        assertThat(deck.categories())
                .flatExtracting(CardDeckCategoryDto::cards)
                .singleElement()
                .satisfies(card -> {
                    assertThat(card.quantity()).isZero();
                    assertThat(card.name()).isEqualTo("???");
                    assertThat(card.imageUrl()).isNull();
                    assertThat(card.thumbnailUrl()).isNull();
                });
    }

    @Test
    void keepsDeckCategoriesVisibleWhenEveryCardInsideIsUndiscovered() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub("player-sub");
        user.setEmail("player@example.com");

        Card defaultCard = card(
                Card.DEFAULT_CARD_ID,
                "Dos de carte",
                "DEFAULT",
                true,
                0,
                "https://s3.example.com/cards/default/original.jpg",
                "https://s3.example.com/cards/default/thumbnail.jpg"
        );
        CardCategory commonCategory = category(UUID.randomUUID(), "Commun");
        CardCategory rareCategory = category(UUID.randomUUID(), "Rare");
        Card commonCard = card(
                UUID.randomUUID(),
                "Carte commune cachee",
                commonCategory.getName(),
                false,
                1,
                "https://s3.example.com/cards/common/original.jpg",
                "https://s3.example.com/cards/common/thumbnail.jpg"
        );
        commonCard.setCategoryRef(commonCategory);
        Card rareCard = card(
                UUID.randomUUID(),
                "Carte rare cachee",
                rareCategory.getName(),
                false,
                1,
                "https://s3.example.com/cards/rare/original.jpg",
                "https://s3.example.com/cards/rare/thumbnail.jpg"
        );
        rareCard.setCategoryRef(rareCategory);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(user));
        when(collectionRepository.findByUserIdAndCollectionType(user.getId(), CardCollectionType.VIRTUAL))
                .thenReturn(List.of());
        when(cardRepository.findByEnabledTrue()).thenReturn(List.of(defaultCard, rareCard, commonCard));

        CardDeckDto deck = cardService.getDeck("player-sub", CardCollectionType.VIRTUAL);

        assertThat(deck.categories())
                .extracting(CardDeckCategoryDto::category)
                .containsExactly("Commun", "Rare");
        assertThat(deck.categories())
                .extracting(CardDeckCategoryDto::categoryId)
                .containsExactly(commonCategory.getId(), rareCategory.getId());
        assertThat(deck.categories())
                .flatExtracting(CardDeckCategoryDto::cards)
                .allSatisfy(card -> {
                    assertThat(card.name()).isEqualTo("???");
                    assertThat(card.category()).isEqualTo("???");
                    assertThat(card.categoryId()).isNull();
                });
    }

    @Test
    void includesCategoryImagesInDeckCategories() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub("player-sub");
        user.setEmail("player@example.com");

        CardCategory rareCategory = category(UUID.randomUUID(), "Rare");
        rareCategory.setImageUrl("https://s3.example.com/card-categories/rare/original.jpg");
        rareCategory.setThumbnailUrl("https://s3.example.com/card-categories/rare/thumbnail.jpg");
        Card rareCard = card(
                UUID.randomUUID(),
                "Carte rare",
                rareCategory.getName(),
                false,
                1,
                "https://s3.example.com/cards/rare/original.jpg",
                "https://s3.example.com/cards/rare/thumbnail.jpg"
        );
        rareCard.setCategoryRef(rareCategory);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(user));
        when(collectionRepository.findByUserIdAndCollectionType(user.getId(), CardCollectionType.VIRTUAL))
                .thenReturn(List.of());
        when(cardRepository.findByEnabledTrue()).thenReturn(List.of(rareCard));

        CardDeckDto deck = cardService.getDeck("player-sub", CardCollectionType.VIRTUAL);

        assertThat(deck.categories())
                .singleElement()
                .satisfies(category -> {
                    assertThat(category.imageUrl()).isEqualTo("https://s3.example.com/card-categories/rare/original.jpg");
                    assertThat(category.thumbnailUrl()).isEqualTo("https://s3.example.com/card-categories/rare/thumbnail.jpg");
                });
    }

    @Test
    void omitsHiddenCategoriesFromUserDeck() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub("player-sub");
        user.setEmail("player@example.com");

        CardCategory visibleCategory = category(UUID.randomUUID(), "Visible");
        CardCategory hiddenCategory = category(UUID.randomUUID(), "Secret");
        hiddenCategory.setHidden(true);

        Card visibleCard = card(
                UUID.randomUUID(),
                "Carte visible",
                visibleCategory.getName(),
                false,
                1,
                "https://s3.example.com/cards/visible/original.jpg",
                "https://s3.example.com/cards/visible/thumbnail.jpg"
        );
        visibleCard.setCategoryRef(visibleCategory);
        Card hiddenCard = card(
                UUID.randomUUID(),
                "Carte secrete",
                hiddenCategory.getName(),
                false,
                1,
                "https://s3.example.com/cards/secret/original.jpg",
                "https://s3.example.com/cards/secret/thumbnail.jpg"
        );
        hiddenCard.setCategoryRef(hiddenCategory);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(user));
        when(collectionRepository.findByUserIdAndCollectionType(user.getId(), CardCollectionType.VIRTUAL))
                .thenReturn(List.of());
        when(cardRepository.findByEnabledTrue()).thenReturn(List.of(visibleCard, hiddenCard));

        CardDeckDto deck = cardService.getDeck("player-sub", CardCollectionType.VIRTUAL);

        assertThat(deck.totalCount()).isEqualTo(1);
        assertThat(deck.categories())
                .extracting(CardDeckCategoryDto::category)
                .containsExactly("Visible");
        assertThat(deck.categories())
                .flatExtracting(CardDeckCategoryDto::cards)
                .extracting(CardDeckCardDto::id)
                .doesNotContain(hiddenCard.getId());
    }

    @Test
    void keepsRealImageForDiscoveredCards() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub("player-sub");
        user.setEmail("player@example.com");

        Card defaultCard = card(
                Card.DEFAULT_CARD_ID,
                "Dos de carte",
                "DEFAULT",
                true,
                0,
                "https://s3.example.com/cards/default/original.jpg",
                "https://s3.example.com/cards/default/thumbnail.jpg"
        );
        Card discoveredCard = card(
                UUID.randomUUID(),
                "Carte trouvee",
                "Rare",
                false,
                1,
                "https://s3.example.com/cards/discovered/original.jpg",
                "https://s3.example.com/cards/discovered/thumbnail.jpg"
        );
        CardCategory category = category(UUID.randomUUID(), "Rare");
        category.setFirstAcquisitionPoints(120);
        category.setDuplicatePoints(20);
        discoveredCard.setCategoryRef(category);
        discoveredCard.setDuplicatePoints(5);
        CardCollection collection = new CardCollection();
        collection.setUser(user);
        collection.setCard(discoveredCard);
        collection.setCollectionType(CardCollectionType.PHYSICAL);
        collection.setQuantity(2);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(user));
        when(collectionRepository.findByUserIdAndCollectionType(user.getId(), CardCollectionType.PHYSICAL))
                .thenReturn(List.of(collection));
        when(cardRepository.findByEnabledTrue()).thenReturn(List.of(defaultCard, discoveredCard));

        CardDeckDto deck = cardService.getDeck("player-sub", CardCollectionType.PHYSICAL);

        assertThat(deck.categories())
                .flatExtracting(CardDeckCategoryDto::cards)
                .filteredOn(card -> card.id().equals(discoveredCard.getId()))
                .singleElement()
                .satisfies(card -> {
                    assertThat(card.quantity()).isEqualTo(2);
                    assertThat(card.imageUrl()).isEqualTo(discoveredCard.getImageUrl());
                    assertThat(card.thumbnailUrl()).isEqualTo(discoveredCard.getThumbnailUrl());
                    assertThat(card.firstAcquisitionPoints()).isEqualTo(120);
                    assertThat(card.duplicatePoints()).isEqualTo(5);
                });
    }

    @Test
    void omitsDefaultCardFromUserDeck() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub("player-sub");
        user.setEmail("player@example.com");

        Card defaultCard = card(
                Card.DEFAULT_CARD_ID,
                "Dos de carte",
                "DEFAULT",
                true,
                0,
                "https://s3.example.com/cards/default/original.jpg",
                "https://s3.example.com/cards/default/thumbnail.jpg"
        );

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(user));
        when(collectionRepository.findByUserIdAndCollectionType(user.getId(), CardCollectionType.VIRTUAL))
                .thenReturn(List.of());
        when(cardRepository.findByEnabledTrue()).thenReturn(List.of(defaultCard));

        CardDeckDto deck = cardService.getDeck("player-sub", null);

        assertThat(deck.ownedCount()).isZero();
        assertThat(deck.totalCount()).isZero();
        assertThat(deck.categories()).isEmpty();
    }

    @Test
    void defaultCardUpdateOnlyAllowsImageReplacement() {
        Card defaultCard = card(
                Card.DEFAULT_CARD_ID,
                "Dos de carte",
                "DEFAULT",
                true,
                0,
                "https://s3.example.com/cards/default/original.jpg",
                "https://s3.example.com/cards/default/thumbnail.jpg"
        );
        when(cardRepository.findById(Card.DEFAULT_CARD_ID)).thenReturn(Optional.of(defaultCard));

        assertThatThrownBy(() -> cardService.update(
                Card.DEFAULT_CARD_ID,
                "Nouveau nom",
                null,
                null,
                null,
                null,
                null,
                null
        )).hasMessage("Seule l'image de la carte par défaut peut être modifiée.");
    }

    @Test
    void collectionCardCannotBecomeDefaultCard() {
        UUID cardId = UUID.randomUUID();
        Card card = card(
                cardId,
                "Carte",
                "Rare",
                false,
                1,
                "https://s3.example.com/cards/card/original.jpg",
                "https://s3.example.com/cards/card/thumbnail.jpg"
        );
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> cardService.update(
                cardId,
                null,
                null,
                null,
                null,
                true,
                null,
                null
        )).hasMessage("Une carte de collection ne peut pas devenir la carte par défaut.");
    }

    @Test
    void grantsCardsIntoRequestedCollectionType() {
        UUID cardId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setKeycloakSub("player-sub");
        user.setEmail("player@example.com");
        Card card = card(
                cardId,
                "Carte",
                "Rare",
                false,
                1,
                "https://s3.example.com/cards/card/original.jpg",
                "https://s3.example.com/cards/card/thumbnail.jpg"
        );

        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(collectionRepository.findByUserIdAndCardIdAndCollectionType(
                user.getId(),
                cardId,
                CardCollectionType.VIRTUAL
        )).thenReturn(Optional.empty());
        when(collectionRepository.save(any(CardCollection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        cardService.grant(
                cardId,
                CardCollectionType.VIRTUAL,
                new CardCollectionGrantRequest(user.getId(), 2),
                "sub-admin"
        );

        ArgumentCaptor<CardCollection> collectionCaptor = ArgumentCaptor.forClass(CardCollection.class);
        verify(collectionRepository).save(collectionCaptor.capture());
        CardCollection collection = collectionCaptor.getValue();
        assertThat(collection.getCollectionType()).isEqualTo(CardCollectionType.VIRTUAL);
        assertThat(collection.getQuantity()).isEqualTo(2);
    }

    @Test
    void physicalCardsCannotBeGrantedWithoutAUniqueInstance() {
        assertThatThrownBy(() -> cardService.grant(
                UUID.randomUUID(),
                CardCollectionType.PHYSICAL,
                new CardCollectionGrantRequest(UUID.randomUUID(), 1),
                "sub-admin"
        )).hasMessage("Les cartes physiques doivent être attribuées via une instance unique.");
    }

    /**
     * Donner des cartes depuis l'ecran des cartes est le meme geste que depuis
     * la fiche joueur : il laisse la meme trace, sinon l'administrateur qui
     * veut donner sans etre vu change simplement d'ecran.
     */
    @Test
    void grantingFromTheCardsScreenWritesAVirtualGrantedLine() {
        UUID cardId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        Card card = card(
                cardId,
                "Carte Rare",
                "Rare",
                false,
                1,
                "https://s3.example.com/cards/card/original.jpg",
                "https://s3.example.com/cards/card/thumbnail.jpg"
        );

        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(collectionRepository.findByUserIdAndCardIdAndCollectionType(
                user.getId(), cardId, CardCollectionType.VIRTUAL)).thenReturn(Optional.empty());
        when(collectionRepository.save(any(CardCollection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        cardService.grant(
                cardId,
                CardCollectionType.VIRTUAL,
                new CardCollectionGrantRequest(user.getId(), 2),
                "sub-admin"
        );

        verify(journal).record("sub-admin", user.getId(), AdminActionType.VIRTUAL_CARDS_GRANTED,
                Map.of("cardId", cardId.toString(), "cardName", "Carte Rare", "quantity", 2));
    }

    /** Un don qui echoue ne laisse aucune ligne : le registre suit la transaction. */
    @Test
    void aRefusedGrantWritesNothing() {
        UUID cardId = UUID.randomUUID();
        Card defaultCard = card(
                cardId,
                "Dos de carte",
                "Rare",
                true,
                1,
                "https://s3.example.com/cards/card/original.jpg",
                "https://s3.example.com/cards/card/thumbnail.jpg"
        );
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(defaultCard));

        assertThatThrownBy(() -> cardService.grant(
                cardId,
                CardCollectionType.VIRTUAL,
                new CardCollectionGrantRequest(UUID.randomUUID(), 1),
                "sub-admin"
        )).hasMessage("La carte par défaut ne peut pas être attribuée.");

        verify(journal, never()).record(any(), any(), any(), any());
    }

    private Card card(
            UUID id,
            String name,
            String category,
            boolean defaultCard,
            int displayOrder,
            String imageUrl,
            String thumbnailUrl
    ) {
        Card card = new Card();
        card.setId(id);
        card.setName(name);
        card.setCategory(category);
        card.setDefaultCard(defaultCard);
        card.setEnabled(true);
        card.setDisplayOrder(displayOrder);
        card.setImageObjectKey("cards/" + id + "/original.jpg");
        card.setThumbnailObjectKey("cards/" + id + "/thumbnail.jpg");
        card.setImageUrl(imageUrl);
        card.setThumbnailUrl(thumbnailUrl);
        card.setContentType("image/jpeg");
        return card;
    }

    @Test
    void categoriesFollowTheOrderSetInAdminRatherThanTheAlphabet() {
        AppUser player = new AppUser();
        player.setId(UUID.randomUUID());

        // L'ordre alphabétique remonte « Légendaire » avant « Peu commune »,
        // exactement l'inverse de ce que l'admin a réglé.
        CardCategory commune = category(UUID.randomUUID(), "Commune");
        commune.setDisplayOrder(1);
        CardCategory peuCommune = category(UUID.randomUUID(), "Peu commune");
        peuCommune.setDisplayOrder(2);
        CardCategory legendaire = category(UUID.randomUUID(), "Légendaire");
        legendaire.setDisplayOrder(4);

        Card first = card(UUID.randomUUID(), "Commune 1", "Commune", false, 1, null, null);
        first.setCategoryRef(commune);
        Card second = card(UUID.randomUUID(), "Peu commune 1", "Peu commune", false, 1, null, null);
        second.setCategoryRef(peuCommune);
        Card third = card(UUID.randomUUID(), "Légendaire 1", "Légendaire", false, 1, null, null);
        third.setCategoryRef(legendaire);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(collectionRepository.findByUserIdAndCollectionType(player.getId(), CardCollectionType.VIRTUAL))
                .thenReturn(List.of());
        // Renvoyées dans le désordre : c'est le service qui doit ranger.
        when(cardRepository.findByEnabledTrue()).thenReturn(List.of(third, first, second));

        CardDeckDto deck = cardService.getDeck("player-sub", CardCollectionType.VIRTUAL);

        assertThat(deck.categories()).extracting(CardDeckCategoryDto::category)
                .containsExactly("Commune", "Peu commune", "Légendaire");
    }

    @Test
    void aCategoryWithoutARankFallsToTheEndInsteadOfDisplacingTheOthers() {
        AppUser player = new AppUser();
        player.setId(UUID.randomUUID());

        CardCategory ranked = category(UUID.randomUUID(), "Zébulon");
        ranked.setDisplayOrder(1);
        Card rankedCard = card(UUID.randomUUID(), "Zébulon 1", "Zébulon", false, 1, null, null);
        rankedCard.setCategoryRef(ranked);
        // Carte héritée : sa catégorie n'existe que par son nom, sans rang.
        Card orphan = card(UUID.randomUUID(), "Ancienne", "Ancienne", false, 1, null, null);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(collectionRepository.findByUserIdAndCollectionType(player.getId(), CardCollectionType.VIRTUAL))
                .thenReturn(List.of());
        when(cardRepository.findByEnabledTrue()).thenReturn(List.of(orphan, rankedCard));

        CardDeckDto deck = cardService.getDeck("player-sub", CardCollectionType.VIRTUAL);

        assertThat(deck.categories()).extracting(CardDeckCategoryDto::category)
                .containsExactly("Zébulon", "Ancienne");
    }

    @Test
    void theTestModeShowsEveryCardAsOwnedWithoutWritingAnything() {
        AppUser player = new AppUser();
        player.setId(UUID.randomUUID());
        Card missing = card(UUID.randomUUID(), "Comète", "Commune", false, 1, null, null);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(collectionRepository.findByUserIdAndCollectionType(player.getId(), CardCollectionType.VIRTUAL))
                .thenReturn(List.of());
        when(cardRepository.findByEnabledTrue()).thenReturn(List.of(missing));
        when(collectionTestMode.allCardsOwned()).thenReturn(true);

        CardDeckDto deck = cardService.getDeck("player-sub", CardCollectionType.VIRTUAL);

        assertThat(deck.categories()).flatExtracting(CardDeckCategoryDto::cards)
                .singleElement()
                .satisfies(card -> {
                    assertThat(card.quantity()).isEqualTo(1);
                    // La carte se révèle : c'est le point, on veut pouvoir s'en servir.
                    assertThat(card.name()).isEqualTo("Comète");
                });
        // Rien n'est écrit : le mode ouvre l'usage, il ne distribue pas.
        verify(collectionRepository, never()).save(any());
    }

    @Test
    void theRealCollectionIgnoresTheTestMode() {
        AppUser player = new AppUser();
        player.setId(UUID.randomUUID());
        Card missing = card(UUID.randomUUID(), "Comète", "Commune", false, 1, null, null);

        when(userRepository.findByKeycloakSub("player-sub")).thenReturn(Optional.of(player));
        when(collectionRepository.findByUserIdAndCollectionType(player.getId(), CardCollectionType.VIRTUAL))
                .thenReturn(List.of());
        when(cardRepository.findByEnabledTrue()).thenReturn(List.of(missing));

        CardDeckDto deck = cardService.getOwnedDeck("player-sub", CardCollectionType.VIRTUAL);

        assertThat(deck.categories()).flatExtracting(CardDeckCategoryDto::cards)
                .singleElement()
                .satisfies(card -> assertThat(card.quantity()).isZero());
        // Le mode n'est même pas consulté : cette lecture dit la vérité, toujours.
        verify(collectionTestMode, never()).allCardsOwned();
    }

    private CardCategory category(UUID id, String name) {
        CardCategory category = new CardCategory();
        category.setId(id);
        category.setName(name);
        return category;
    }

    private CardTeamMember teamMember(CardTeam team, AppUser user) {
        CardTeamMember member = new CardTeamMember();
        member.setTeam(team);
        member.setUser(user);
        return member;
    }

    private CardCollection collection(AppUser user, Card card, int quantity) {
        CardCollection collection = new CardCollection();
        collection.setUser(user);
        collection.setCard(card);
        collection.setCollectionType(CardCollectionType.PHYSICAL);
        collection.setQuantity(quantity);
        return collection;
    }

    private MockMultipartFile imageFile() {
        return new MockMultipartFile(
                "image",
                "card.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );
    }

    // ── Suppression d'une definition de carte ────────────────────────────────

    private Card deletableCard() {
        Card card = new Card();
        card.setId(UUID.randomUUID());
        card.setName("Carte a supprimer");
        return card;
    }

    /**
     * Rien de physique : la carte part, et les cascades emportent le reste.
     *
     * <p>Les paliers de butin qui la distribuaient partent avec elle -- une carte retiree
     * du catalogue ne peut plus tomber. C'est la base qui s'en charge (V134), pas ce
     * code : le test verifie seulement qu'on ne l'arrete pas.</p>
     */
    @Test
    void aCardNothingPhysicalDependsOnIsDeleted() {
        Card card = deletableCard();
        when(cardRepository.findById(card.getId())).thenReturn(Optional.of(card));
        when(cardInstanceRepository.countByCardId(card.getId())).thenReturn(0L);
        when(printRunSlotRepository.countByCardId(card.getId())).thenReturn(0L);

        cardService.delete(card.getId());

        verify(cardRepository).delete(card);
    }

    /**
     * La carte quitte les paliers de butin par JPA, jamais par une cascade.
     *
     * <p>Les deux tables de paliers portent leur position dans leur cle primaire et se
     * lisent par {@code @OrderColumn} : Hibernate comble les trous avec des null. Une
     * ligne retiree par la base laisserait donc un trou, et la premiere lecture du pack
     * partirait en NullPointerException -- sur GET /api/decks/{id}, qui n'a rien a voir
     * avec la suppression. C'est exactement ce qui est arrive.</p>
     */
    @Test
    void aDeletedCardLeavesItsDropTiersThroughJpa() {
        Card card = deletableCard();
        Deck deck = new Deck();
        DropTier tier = new DropTier();
        Card other = deletableCard();
        tier.setCards(new java.util.ArrayList<>(java.util.List.of(other, card)));
        deck.setDropTiers(new java.util.ArrayList<>(java.util.List.of(tier)));

        when(cardRepository.findById(card.getId())).thenReturn(Optional.of(card));
        when(deckRepository.findAll()).thenReturn(java.util.List.of(deck));
        when(dropTierTemplateRepository.findAll()).thenReturn(java.util.List.of());

        cardService.delete(card.getId());

        // La liste se referme sur elle-meme : plus de carte supprimee, et surtout pas de
        // trou a sa place.
        assertThat(tier.getCards()).containsExactly(other);
        verify(deckRepository).flush();
        verify(cardRepository).delete(card);
    }

    /** Refusee, la suppression ne touche pas non plus aux paliers. */
    @Test
    void aRefusedDeletionLeavesTheDropTiersAlone() {
        Card card = deletableCard();
        when(cardRepository.findById(card.getId())).thenReturn(Optional.of(card));
        when(cardInstanceRepository.countByCardId(card.getId())).thenReturn(3L);

        assertThatThrownBy(() -> cardService.delete(card.getId()))
                .isInstanceOf(ConflictException.class);

        verify(deckRepository, never()).findAll();
    }

    /**
     * Un exemplaire imprime est dans la main de quelqu'un : la definition reste.
     *
     * <p>La base le refuse deja par {@code ON DELETE RESTRICT}, mais son refus remonte en
     * 500 illisible jusqu'a l'ecran. On pose donc la question avant, pour pouvoir la
     * dire -- et le message porte le nombre, sans quoi il faudrait le chercher.</p>
     */
    @Test
    void aCardWithPrintedCopiesIsRefusedWithTheirCount() {
        Card card = deletableCard();
        when(cardRepository.findById(card.getId())).thenReturn(Optional.of(card));
        when(cardInstanceRepository.countByCardId(card.getId())).thenReturn(7L);
        when(printRunSlotRepository.countByCardId(card.getId())).thenReturn(0L);

        assertThatThrownBy(() -> cardService.delete(card.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("7")
                .hasMessageContaining("exemplaires imprimés");

        verify(cardRepository, never()).delete(any(Card.class));
    }

    @Test
    void aCardStillOnAPrintSheetIsRefusedToo() {
        Card card = deletableCard();
        when(cardRepository.findById(card.getId())).thenReturn(Optional.of(card));
        when(cardInstanceRepository.countByCardId(card.getId())).thenReturn(0L);
        when(printRunSlotRepository.countByCardId(card.getId())).thenReturn(1L);

        assertThatThrownBy(() -> cardService.delete(card.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("1 case de planche");

        verify(cardRepository, never()).delete(any(Card.class));
    }

    /** Les deux a la fois se disent dans la meme phrase. */
    @Test
    void bothReasonsAreNamedTogether() {
        Card card = deletableCard();
        when(cardRepository.findById(card.getId())).thenReturn(Optional.of(card));
        when(cardInstanceRepository.countByCardId(card.getId())).thenReturn(2L);
        when(printRunSlotRepository.countByCardId(card.getId())).thenReturn(3L);

        assertThatThrownBy(() -> cardService.delete(card.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("2 exemplaires imprimés")
                .hasMessageContaining("3 cases de planche");
    }
}
