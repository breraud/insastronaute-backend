package fr.insastronaute.webapi.card.service;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardCategory;
import fr.insastronaute.webapi.card.domain.CardCollection;
import fr.insastronaute.webapi.card.domain.CardCollectionSettings;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.domain.CardGrouping;
import fr.insastronaute.webapi.card.domain.CardCollectionScope;
import fr.insastronaute.webapi.card.domain.CardType;
import fr.insastronaute.webapi.card.goofy.GoofyCardService;
import fr.insastronaute.webapi.card.dto.CardCollectionGrantRequest;
import fr.insastronaute.webapi.card.dto.CardDeckCardDto;
import fr.insastronaute.webapi.card.dto.CardDeckCategoryDto;
import fr.insastronaute.webapi.card.dto.CardDeckDto;
import fr.insastronaute.webapi.card.dto.CardDeckTeamDto;
import fr.insastronaute.webapi.card.dto.CardDto;
import fr.insastronaute.webapi.card.dto.CardOrderUpdateRequest;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.repository.CardCategoryRepository;
import fr.insastronaute.webapi.card.repository.CardInstanceRepository;
import fr.insastronaute.webapi.card.print.repository.CardPrintRunSlotRepository;
import fr.insastronaute.webapi.deck.domain.Deck;
import fr.insastronaute.webapi.deck.domain.DropTier;
import fr.insastronaute.webapi.deck.domain.DropTierTemplate;
import fr.insastronaute.webapi.deck.domain.DropTierTemplateTier;
import fr.insastronaute.webapi.deck.repository.DeckRepository;
import fr.insastronaute.webapi.deck.repository.DropTierTemplateRepository;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.card.repository.CardTypeRepository;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import fr.insastronaute.webapi.cardteam.domain.CardTeam;
import fr.insastronaute.webapi.cardteam.domain.CardTeamMember;
import fr.insastronaute.webapi.cardteam.repository.CardTeamMemberRepository;
import fr.insastronaute.webapi.cardteam.repository.CardTeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CardService {

    private static final String UNDISCOVERED_PLACEHOLDER = "???";
    private static final String COVER_NAME = "Card cover";

    private final CardRepository cardRepository;
    private final CardCategoryRepository categoryRepository;
    private final CardTypeRepository typeRepository;
    private final CardCollectionRepository collectionRepository;
    private final CardInstanceRepository cardInstanceRepository;
    private final CardPrintRunSlotRepository printRunSlotRepository;
    private final DeckRepository deckRepository;
    private final DropTierTemplateRepository dropTierTemplateRepository;
    private final UserRepository userRepository;
    private final CardImageService imageService;
    private final CardTeamMemberRepository teamMemberRepository;
    private final CardTeamRepository teamRepository;
    private final AutoChallengeTriggerPublisher autoChallengeTriggerPublisher;
    private final CollectionRewardService collectionRewardService;
    private final CollectionPointsCalculator collectionPointsCalculator;
    private final CollectionTestModeService collectionTestMode;
    private final GoofyCardService goofyCardService;
    private final AdminActionJournal journal;

    public CardService(
            CardRepository cardRepository,
            CardCategoryRepository categoryRepository,
            CardTypeRepository typeRepository,
            CardCollectionRepository collectionRepository,
            UserRepository userRepository,
            CardImageService imageService,
            CardTeamMemberRepository teamMemberRepository,
            CardTeamRepository teamRepository,
            AutoChallengeTriggerPublisher autoChallengeTriggerPublisher,
            CollectionRewardService collectionRewardService,
            CollectionPointsCalculator collectionPointsCalculator,
            AdminActionJournal journal,
            CollectionTestModeService collectionTestMode,
            GoofyCardService goofyCardService,
            CardInstanceRepository cardInstanceRepository,
            CardPrintRunSlotRepository printRunSlotRepository,
            DeckRepository deckRepository,
            DropTierTemplateRepository dropTierTemplateRepository
    ) {
        this.cardRepository = cardRepository;
        this.categoryRepository = categoryRepository;
        this.typeRepository = typeRepository;
        this.collectionRepository = collectionRepository;
        this.userRepository = userRepository;
        this.imageService = imageService;
        this.teamMemberRepository = teamMemberRepository;
        this.teamRepository = teamRepository;
        this.autoChallengeTriggerPublisher = autoChallengeTriggerPublisher;
        this.collectionRewardService = collectionRewardService;
        this.collectionPointsCalculator = collectionPointsCalculator;
        this.collectionTestMode = collectionTestMode;
        this.goofyCardService = goofyCardService;
        this.journal = journal;
        this.cardInstanceRepository = cardInstanceRepository;
        this.printRunSlotRepository = printRunSlotRepository;
        this.deckRepository = deckRepository;
        this.dropTierTemplateRepository = dropTierTemplateRepository;
    }

    @Transactional(readOnly = true)
    public CardDeckDto getDeck(String keycloakSub, CardCollectionType collectionType) {
        return getDeck(keycloakSub, collectionType, CardGrouping.RANK);
    }

    @Transactional(readOnly = true)
    public CardDeckDto getDeck(String keycloakSub, CardCollectionType collectionType, CardGrouping grouping) {
        return getDeck(keycloakSub, collectionType, grouping, CardCollectionScope.PERSONAL);
    }

    /**
     * La collection réellement possédée, mode de test ou non.
     *
     * Ce que le joueur voit et ce qu'il possède sont deux choses différentes dès
     * que le mode de test est levé. Qui décide d'une récompense doit lire
     * celle-ci, sinon il conclurait que tout est déjà acquis et ne donnerait
     * plus rien — en silence.
     */
    @Transactional(readOnly = true)
    public CardDeckDto getOwnedDeck(String keycloakSub, CardCollectionType collectionType) {
        return getDeck(keycloakSub, collectionType, CardGrouping.RANK, CardCollectionScope.PERSONAL, false);
    }

    /**
     * L'annotation compte autant que la methode : `open-in-view` est desactive, et sans
     * transaction la categorie chargee paresseusement n'est plus qu'un mandataire mort.
     */
    @Transactional(readOnly = true)
    public CardDeckDto getDeck(
            String keycloakSub,
            CardCollectionType collectionType,
            CardGrouping grouping,
            CardCollectionScope scope
    ) {
        return getDeck(keycloakSub, collectionType, grouping, scope, collectionTestMode.allCardsOwned());
    }

    @Transactional(readOnly = true)
    public CardDeckDto getTeamDeck(UUID teamId) {
        CardTeam team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Equipe introuvable."));
        List<UUID> memberIds = teamMemberRepository.findByTeamIdOrderByJoinedAt(teamId).stream()
                .map(CardTeamMember::getUser)
                .map(AppUser::getId)
                .toList();
        List<CardCollection> collections = collectionRepository.findByUserIdsAndCollectionType(
                memberIds, CardCollectionType.PHYSICAL
        );
        return buildDeck(
                CardCollectionType.PHYSICAL,
                CardGrouping.RANK,
                CardCollectionScope.TEAM,
                team,
                memberIds.size(),
                collections,
                false
        );
    }

    private CardDeckDto getDeck(
            String keycloakSub,
            CardCollectionType collectionType,
            CardGrouping grouping,
            CardCollectionScope scope,
            boolean everythingOwned
    ) {
        CardCollectionType resolvedCollectionType = normalizeCollectionType(collectionType);
        CardCollectionScope resolvedScope = scope == null ? CardCollectionScope.PERSONAL : scope;
        if (resolvedScope == CardCollectionScope.TEAM && resolvedCollectionType != CardCollectionType.PHYSICAL) {
            throw new BadRequestException("La collection d'equipe est reservee aux cartes physiques.");
        }
        AppUser user = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        CardTeam team = null;
        List<CardCollection> ownedCollections;
        int teamMemberCount = 0;
        if (resolvedScope == CardCollectionScope.TEAM) {
            // Un joueur sans equipe est une equipe d'un : sa collection commune est la
            // sienne. Refuser l'onglet lui montrait une erreur rouge la ou il n'y a rien
            // d'anormal — et l'onglet est offert a tout le monde, equipe ou pas.
            Optional<CardTeamMember> membership = teamMemberRepository.findByUserId(user.getId());
            team = membership.map(CardTeamMember::getTeam).orElse(null);
            List<UUID> memberIds = team == null
                    ? List.of(user.getId())
                    : teamMemberRepository.findByTeamIdOrderByJoinedAt(team.getId()).stream()
                            .map(CardTeamMember::getUser)
                            .map(AppUser::getId)
                            .toList();
            teamMemberCount = memberIds.size();
            ownedCollections = collectionRepository.findByUserIdsAndCollectionType(
                    memberIds, CardCollectionType.PHYSICAL
            );
        } else {
            ownedCollections = collectionRepository.findByUserIdAndCollectionType(user.getId(), resolvedCollectionType);
        }
        return buildDeck(
                resolvedCollectionType,
                grouping,
                resolvedScope,
                team,
                teamMemberCount,
                ownedCollections,
                everythingOwned
        );
    }

    private CardDeckDto buildDeck(
            CardCollectionType resolvedCollectionType,
            CardGrouping grouping,
            CardCollectionScope resolvedScope,
            CardTeam team,
            int teamMemberCount,
            List<CardCollection> ownedCollections,
            boolean everythingOwned
    ) {
        Map<UUID, CardCollection> collectionByCardId = aggregateCollections(ownedCollections);

        List<Card> enabledCards = cardRepository.findByEnabledTrue();
        Card defaultCard = enabledCards.stream()
                .filter(Card::isDefaultCard)
                .findFirst()
                .orElse(null);
        Optional<UUID> goofyCardId = goofyCardService.configuredCardId();

        // Une seule lecture des réglages pour tout le deck, pas une par carte.
        CardCollectionSettings deckSettings = collectionPointsCalculator.settings();
        List<DeckCard> deckCards = enabledCards.stream()
                .filter(card -> !card.isDefaultCard())
                .filter(card -> card.getCategoryRef() == null
                        || (!card.getCategoryRef().isHidden() && !card.getCategoryRef().isGame()))
                .filter(card -> resolvedScope != CardCollectionScope.TEAM
                        || card.getCategoryRef() == null
                        || !card.getCategoryRef().isExtension())
                .filter(card -> isVisibleInDeck(card, collectionByCardId, goofyCardId, everythingOwned))
                .map(card -> new DeckCard(card, toDeckCard(
                        card, collectionByCardId.get(card.getId()), defaultCard, deckSettings, everythingOwned,
                        goofyCardId.orElse(null))))
                .sorted(deckCardComparator())
                .toList();

        CardGrouping resolvedGrouping = grouping == null ? CardGrouping.RANK : grouping;
        Map<String, List<DeckCard>> cardsByCategory = deckCards.stream()
                .sorted(resolvedGrouping == CardGrouping.TYPE
                        ? Comparator.comparing(deckCard -> typeGroupName(deckCard.card()), String.CASE_INSENSITIVE_ORDER)
                        : deckCardComparator())
                .collect(Collectors.groupingBy(
                        deckCard -> resolvedGrouping == CardGrouping.TYPE
                                ? typeGroupName(deckCard.card())
                                : deckCard.card().getCategory(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<CardDeckCategoryDto> categories = cardsByCategory.entrySet().stream()
                .map(entry -> new CardDeckCategoryDto(
                        resolvedGrouping == CardGrouping.TYPE
                                ? entry.getValue().getFirst().card().getTypeId()
                                : entry.getValue().getFirst().card().getCategoryId(),
                        entry.getKey(),
                        resolvedGrouping == CardGrouping.TYPE || entry.getValue().getFirst().card().getCategoryRef() == null
                                ? null
                                : entry.getValue().getFirst().card().getCategoryRef().getImageUrl(),
                        resolvedGrouping == CardGrouping.TYPE || entry.getValue().getFirst().card().getCategoryRef() == null
                                ? null
                                : entry.getValue().getFirst().card().getCategoryRef().getThumbnailUrl(),
                        entry.getValue().stream()
                                .filter(deckCard -> isCounted(deckCard, goofyCardId))
                                .map(DeckCard::dto).filter(CardService::isOwned).count(),
                        (int) entry.getValue().stream()
                                .filter(deckCard -> isCounted(deckCard, goofyCardId))
                                .count(),
                        entry.getValue().stream().map(DeckCard::dto).toList()
                ))
                .toList();

        long teamScore = resolvedScope == CardCollectionScope.TEAM
                ? deckCards.stream()
                        .map(DeckCard::dto)
                        .filter(CardService::isOwned)
                        .mapToLong(card -> card.collectionPoints() == null ? 0L : card.collectionPoints())
                        .sum()
                : 0L;
        List<DeckCard> countedCards = deckCards.stream()
                .filter(deckCard -> isCounted(deckCard, goofyCardId))
                .toList();
        return new CardDeckDto(
                countedCards.stream().map(DeckCard::dto).filter(CardService::isOwned).count(),
                countedCards.size(),
                categories,
                resolvedScope,
                team == null ? null : new CardDeckTeamDto(team.getId(), team.getName(), teamMemberCount, CardTeam.MAX_MEMBERS),
                team == null ? null : teamScore
        );
    }

    private Map<UUID, CardCollection> aggregateCollections(List<CardCollection> collections) {
        Map<UUID, CardCollection> aggregated = new LinkedHashMap<>();
        for (CardCollection collection : collections) {
            UUID cardId = collection.getCard().getId();
            CardCollection existing = aggregated.get(cardId);
            if (existing == null) {
                CardCollection copy = new CardCollection();
                copy.setUser(collection.getUser());
                copy.setCard(collection.getCard());
                copy.setCollectionType(collection.getCollectionType());
                copy.setQuantity(collection.getQuantity());
                aggregated.put(cardId, copy);
            } else {
                existing.setQuantity(existing.getQuantity() + collection.getQuantity());
            }
        }
        return aggregated;
    }

    @Transactional(readOnly = true)
    public List<CardDto> findAll() {
        return cardRepository.findAll().stream()
                .filter(card -> !card.isDefaultCard())
                .sorted(cardComparator())
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CardDto> findAllForSts() {
        return cardRepository.findAll().stream()
                .filter(card -> !card.isDefaultCard())
                .sorted(cardComparator())
                .map(this::toDto)
                .toList();
    }

    /**
     * La catégorie de chaque carte de collection, par nom, sans aucun filtre de visibilité.
     *
     * <p>Le deck d'un joueur écarte les catégories cachées et les cartes désactivées, et c'est
     * juste pour ce qu'il affiche. Mais une catégorie retirée — « Ancienne collection », « HS »
     * — est justement cachée <em>parce qu'</em>elle n'est plus obtenable, et c'est exactement
     * elle que {@code runEndUnlockableCategories} désigne. Lue à travers le deck, elle
     * n'apparaissait jamais : sa carte n'avait plus de catégorie, n'était donc exemptée de rien,
     * et le déverrouillage de fin de run l'écartait en silence.
     *
     * <p>Transactionnel pour la même raison que {@link #findAllForSts} : la catégorie se charge
     * paresseusement et {@code open-in-view} est désactivé.
     */
    @Transactional(readOnly = true)
    public Map<String, String> categoryNamesByCardName() {
        Map<String, String> categories = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Card card : cardRepository.findAll()) {
            if (card.isDefaultCard() || card.getName() == null || card.getName().isBlank()) {
                continue;
            }
            String category = card.getCategory();
            if (category != null && !category.isBlank()) {
                categories.put(card.getName().trim(), category.trim());
            }
        }
        return categories;
    }

    @Transactional(readOnly = true)
    public CardDto getCover() {
        return cardRepository.findByDefaultCardTrue()
                .map(this::toDto)
                .orElseThrow(() -> new NotFoundException("Dos de carte introuvable."));
    }

    @Transactional
    public CardDto create(
            String name,
            UUID categoryId,
            String category,
            UUID typeId,
            Boolean enabled,
            Boolean defaultCard,
            Integer displayOrder,
            Integer firstAcquisitionPoints,
            Integer duplicatePoints,
            MultipartFile image,
            MultipartFile textlessImage
    ) {
        boolean isDefaultCard = Boolean.TRUE.equals(defaultCard);
        if (isDefaultCard) {
            throw new BadRequestException("Utilisez la route dédiée pour modifier le dos de carte.");
        }

        Card card = new Card();
        card.setId(UUID.randomUUID());
        card.setName(normalizeName(name));
        card.setCategoryRef(resolveCategory(categoryId, category));
        card.setTypeRef(resolveType(typeId));
        card.setDefaultCard(false);
        card.setEnabled(enabled == null || enabled);
        card.setDisplayOrder(displayOrder);
        card.setFirstAcquisitionPoints(optionalNonNegative(firstAcquisitionPoints, "Les PC de première obtention"));
        card.setDuplicatePoints(optionalNonNegative(duplicatePoints, "Les PC de doublon"));

        CardImageService.StoredCardImages images = imageService.store(card.getId(), image);
        applyImages(card, images);
        
        if (textlessImage != null && !textlessImage.isEmpty()) {
            CardImageService.StoredCardImages textlessImages = imageService.store(card.getId(), textlessImage);
            card.setTextlessImageObjectKey(textlessImages.imageObjectKey());
            card.setTextlessImageUrl(textlessImages.imageUrl());
        }

        return toDto(cardRepository.save(card));
    }

    public CardDto create(
            String name,
            UUID categoryId,
            String category,
            UUID typeId,
            Boolean enabled,
            Boolean defaultCard,
            Integer displayOrder,
            MultipartFile image
    ) {
        return create(name, categoryId, category, typeId, enabled, defaultCard, displayOrder, null, null, image, null);
    }

    public CardDto create(
            String name,
            UUID categoryId,
            String category,
            Boolean enabled,
            Boolean defaultCard,
            Integer displayOrder,
            MultipartFile image
    ) {
        return create(name, categoryId, category, null, enabled, defaultCard, displayOrder, null, null, image, null);
    }

    @Transactional
    public CardDto update(
            UUID id,
            String name,
            UUID categoryId,
            String category,
            UUID typeId,
            boolean clearType,
            Boolean enabled,
            Boolean defaultCard,
            Integer displayOrder,
            Integer firstAcquisitionPoints,
            Integer duplicatePoints,
            boolean clearFirstAcquisitionPoints,
            boolean clearDuplicatePoints,
            MultipartFile image
    ) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Carte introuvable."));

        if (card.isDefaultCard()) {
            rejectDefaultCardMetadataUpdate(
                    name, categoryId, category, enabled, defaultCard, displayOrder,
                    firstAcquisitionPoints, duplicatePoints, clearFirstAcquisitionPoints, clearDuplicatePoints
            );
        }

        if (name != null) {
            card.setName(normalizeName(name));
        }
        if (categoryId != null || category != null) {
            card.setCategoryRef(resolveCategory(categoryId, category));
        }
        if (clearType) {
            card.setTypeRef(null);
        } else if (typeId != null) {
            card.setTypeRef(resolveType(typeId));
        }
        if (displayOrder != null) {
            card.setDisplayOrder(displayOrder);
        }
        if (defaultCard != null) {
            updateDefaultFlag(card, defaultCard);
        }
        if (enabled != null) {
            if (card.isDefaultCard() && !enabled) {
                throw new BadRequestException("La carte par défaut doit rester activée.");
            }
            card.setEnabled(enabled);
        }
        if (clearFirstAcquisitionPoints) {
            card.setFirstAcquisitionPoints(null);
        } else if (firstAcquisitionPoints != null) {
            card.setFirstAcquisitionPoints(optionalNonNegative(firstAcquisitionPoints, "Les PC de première obtention"));
        }
        if (clearDuplicatePoints) {
            card.setDuplicatePoints(null);
        } else if (duplicatePoints != null) {
            card.setDuplicatePoints(optionalNonNegative(duplicatePoints, "Les PC de doublon"));
        }
        if (card.isDefaultCard()) {
            card.setEnabled(true);
        }

        if (image != null && !image.isEmpty()) {
            String oldImageKey = card.getImageObjectKey();
            String oldThumbnailKey = card.getThumbnailObjectKey();
            CardImageService.StoredCardImages images = imageService.store(card.getId(), image);
            applyImages(card, images);
            scheduleImageCleanup(oldImageKey, oldThumbnailKey);
        }

        return toDto(cardRepository.save(card));
    }

    public CardDto update(
            UUID id,
            String name,
            UUID categoryId,
            String category,
            UUID typeId,
            boolean clearType,
            Boolean enabled,
            Boolean defaultCard,
            Integer displayOrder,
            MultipartFile image
    ) {
        return update(
                id, name, categoryId, category, typeId, clearType, enabled, defaultCard, displayOrder,
                null, null, false, false, image
        );
    }

    public CardDto update(
            UUID id, String name, UUID categoryId, String category,
            Boolean enabled, Boolean defaultCard, Integer displayOrder, MultipartFile image
    ) {
        return update(
                id, name, categoryId, category, null, false, enabled, defaultCard, displayOrder,
                null, null, false, false, image
        );
    }

    @Transactional
    public CardDto updateCover(MultipartFile image) {
        Card card = cardRepository.findById(Card.DEFAULT_CARD_ID)
                .orElseGet(() -> {
                    if (cardRepository.existsByDefaultCardTrue()) {
                        throw new ConflictException("Une carte par défaut existe déjà avec un autre identifiant.");
                    }
                    Card cover = new Card();
                    cover.setId(Card.DEFAULT_CARD_ID);
                    cover.setName(COVER_NAME);
                    cover.setCategory(Card.COVER_CATEGORY);
                    cover.setDefaultCard(true);
                    cover.setEnabled(true);
                    cover.setDisplayOrder(0);
                    return cover;
                });

        String oldImageKey = card.getImageObjectKey();
        String oldThumbnailKey = card.getThumbnailObjectKey();
        CardImageService.StoredCardImages images = imageService.store(card.getId(), image);
        applyImages(card, images);
        card.setEnabled(true);
        CardDto dto = toDto(cardRepository.save(card));
        scheduleImageCleanup(oldImageKey, oldThumbnailKey);
        return dto;
    }

    @Transactional
    public void deleteCover() {
        Card card = cardRepository.findById(Card.DEFAULT_CARD_ID).orElse(null);
        if (card == null) {
            return;
        }
        String oldImageKey = card.getImageObjectKey();
        String oldThumbnailKey = card.getThumbnailObjectKey();
        cardRepository.delete(card);
        scheduleImageCleanup(oldImageKey, oldThumbnailKey);
    }

    @Transactional
    public CardDto updateImage(UUID id, MultipartFile image) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Carte introuvable."));
        String oldImageKey = card.getImageObjectKey();
        String oldThumbnailKey = card.getThumbnailObjectKey();
        CardImageService.StoredCardImages images = imageService.store(card.getId(), image);
        applyImages(card, images);
        CardDto dto = toDto(cardRepository.save(card));
        scheduleImageCleanup(oldImageKey, oldThumbnailKey);
        return dto;
    }

    @Transactional
    public CardDto updateTextlessImage(UUID id, MultipartFile image) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Carte introuvable."));
        String oldImageKey = card.getTextlessImageObjectKey();
        
        CardImageService.StoredCardImages images = imageService.store(card.getId(), image);
        card.setTextlessImageObjectKey(images.imageObjectKey());
        card.setTextlessImageUrl(images.imageUrl());
        
        CardDto dto = toDto(cardRepository.save(card));
        scheduleImageCleanup(oldImageKey);
        return dto;
    }

    @Transactional
    public CardDto deleteTextlessImage(UUID id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Carte introuvable."));
        String oldImageKey = card.getTextlessImageObjectKey();
        
        card.setTextlessImageObjectKey(null);
        card.setTextlessImageUrl(null);
        
        CardDto dto = toDto(cardRepository.save(card));
        scheduleImageCleanup(oldImageKey);
        return dto;
    }

    @Transactional
    public List<CardDto> reorder(UUID categoryId, CardOrderUpdateRequest request) {
        if (request == null || request.cardIds() == null || request.cardIds().isEmpty()) {
            throw new BadRequestException("La liste des cartes est requise.");
        }
        CardCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Catégorie de cartes introuvable."));
        List<Card> cards = cardRepository.findByCategoryRef_IdAndDefaultCardFalse(category.getId());
        Set<UUID> existingIds = cards.stream().map(Card::getId).collect(Collectors.toSet());
        Set<UUID> requestedIds = new HashSet<>(request.cardIds());
        if (requestedIds.size() != request.cardIds().size() || !existingIds.equals(requestedIds)) {
            throw new BadRequestException("La liste doit contenir exactement les cartes de cette catégorie.");
        }

        Map<UUID, Card> cardsById = cards.stream()
                .collect(Collectors.toMap(Card::getId, Function.identity()));
        for (int index = 0; index < request.cardIds().size(); index++) {
            cardsById.get(request.cardIds().get(index)).setDisplayOrder(index + 1);
        }

        return cardRepository.saveAll(cards).stream()
                .sorted(cardComparator())
                .map(this::toDto)
                .toList();
    }

    /**
     * Supprimer une definition de carte.
     *
     * <p>Ce qui n'a plus d'objet sans elle part avec : les exemplaires virtuels des
     * joueurs, les paliers de butin qui la distribuaient, les modeles de paliers. Les
     * cascades en base s'en chargent.</p>
     *
     * <p>Ce qui existe dans le monde reel, non. Un exemplaire physique imprime est dans
     * la main de quelqu'un, une case de planche d'impression est deja partie chez
     * l'imprimeur : supprimer leur definition laisserait ces objets sans rien en face.
     * La base le refuse deja par {@code ON DELETE RESTRICT} -- mais son refus remonte en
     * 500 illisible, alors on pose la question avant, pour pouvoir la dire.</p>
     */
    @Transactional
    public void delete(UUID id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Carte introuvable."));
        if (card.isDefaultCard()) {
            throw new BadRequestException("La carte par défaut ne peut pas être supprimée.");
        }
        requireNothingPhysicalDependsOn(id);
        detachFromOrderedTiers(card);

        cardRepository.delete(card);
        scheduleImageCleanup(
                card.getImageObjectKey(),
                card.getThumbnailObjectKey(),
                card.getTextlessImageObjectKey()
        );
    }

    /**
     * Retirer la carte de chaque palier de butin, par JPA et non par la base.
     *
     * <p>Les deux tables de paliers portent leur position dans leur cle primaire, et JPA
     * les lit par {@code @OrderColumn} : Hibernate reconstruit la liste par indice, et
     * <em>comble les trous avec des null</em>. Une ligne retiree par une cascade
     * laisserait donc un trou, et la premiere lecture du pack partirait en
     * NullPointerException -- sur un endpoint qui n'a rien a voir avec la suppression.</p>
     *
     * <p>Passer par la liste, elle, renumerote les indices : c'est tout l'interet, et
     * c'est pourquoi les deux cles etrangeres restent en {@code ON DELETE RESTRICT}. La
     * base doit refuser ce que ce code n'a pas fait.</p>
     */
    private void detachFromOrderedTiers(Card card) {
        for (Deck deck : deckRepository.findAll()) {
            for (DropTier tier : deck.getDropTiers()) {
                tier.getCards().removeIf(tierCard -> tierCard != null && tierCard.getId().equals(card.getId()));
            }
        }
        for (DropTierTemplate template : dropTierTemplateRepository.findAll()) {
            for (DropTierTemplateTier tier : template.getTiers()) {
                tier.getCards().removeIf(tierCard -> tierCard != null && tierCard.getId().equals(card.getId()));
            }
        }
        deckRepository.flush();
        dropTierTemplateRepository.flush();
    }

    private void requireNothingPhysicalDependsOn(UUID cardId) {
        long instances = cardInstanceRepository.countByCardId(cardId);
        long printedSlots = printRunSlotRepository.countByCardId(cardId);
        if (instances == 0 && printedSlots == 0) {
            return;
        }

        // Le message nomme les deux nombres : « impossible » sans chiffre laisse
        // chercher, et c'est le meme ecran qui permet de retirer les instances.
        StringBuilder reason = new StringBuilder("Cette carte existe en physique et ne peut pas être supprimée : ");
        if (instances > 0) {
            reason.append(instances).append(instances == 1 ? " exemplaire imprimé" : " exemplaires imprimés");
        }
        if (instances > 0 && printedSlots > 0) {
            reason.append(", ");
        }
        if (printedSlots > 0) {
            reason.append(printedSlots).append(printedSlots == 1 ? " case de planche" : " cases de planche");
        }
        reason.append(". Retire-les d'abord.");
        throw new ConflictException(reason.toString());
    }

    /**
     * Don de cartes virtuelles depuis l'ecran des cartes.
     *
     * <p>Seul AdminCardController appelle cette methode : c'est le meme geste
     * que AdminUserService.addCards en VIRTUAL, depuis un autre ecran. Elle
     * journalise donc ici, dans la transaction du geste. Les chemins joueur
     * passent par addVirtualCards, qui reste muet.</p>
     */
    @Transactional
    public CardDeckCardDto grant(
            UUID cardId,
            CardCollectionType collectionType,
            CardCollectionGrantRequest request,
            String actorKeycloakSub
    ) {
        if (request == null) {
            throw new BadRequestException("La demande d'attribution est requise.");
        }
        CardCollectionType resolvedCollectionType = normalizeCollectionType(collectionType);
        if (resolvedCollectionType == CardCollectionType.PHYSICAL) {
            throw new BadRequestException("Les cartes physiques doivent être attribuées via une instance unique.");
        }

        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new NotFoundException("Carte introuvable."));
        if (card.isDefaultCard()) {
            throw new BadRequestException("La carte par défaut ne peut pas être attribuée.");
        }
        AppUser user = userRepository.findById(request.userId())
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        int quantity = request.quantity() == null ? 1 : request.quantity();
        if (quantity < 1) {
            throw new BadRequestException("La quantité doit être supérieure à zéro.");
        }

        CardCollection collection = collectionRepository
                .findByUserIdAndCardIdAndCollectionType(user.getId(), card.getId(), resolvedCollectionType)
                .orElse(null);
        int quantityBefore = collection == null ? 0 : collection.getQuantity();
        if (collection == null) {
            collection = new CardCollection();
            collection.setUser(user);
            collection.setCard(card);
            collection.setCollectionType(resolvedCollectionType);
            collection.setQuantity(quantity);
        } else {
            collection.setQuantity(collection.getQuantity() + quantity);
        }
        CardCollection saved = collectionRepository.save(collection);
        collectionRewardService.credit(user.getId(), card, resolvedCollectionType, quantityBefore, saved.getQuantity());
        autoChallengeTriggerPublisher.publish(user.getId(), AutoChallengeTrigger.CARD_OBTAINED);

        journal.record(actorKeycloakSub, user.getId(), AdminActionType.VIRTUAL_CARDS_GRANTED,
                Map.of("cardId", card.getId().toString(), "cardName", card.getName(), "quantity", quantity));

        return toDeckCard(card, saved, null);
    }

    /**
     * Ajoute des cartes virtuelles et renvoie le nom de la carte touchee.
     *
     * <p>Le nom sert a l'appelant admin, qui le porte dans les details du
     * registre : une ligne qui ne cite qu'un UUID devient illisible des que la
     * definition de carte est supprimee. Le renvoyer ici evite une requete de
     * plus et resout le nom une fois par geste. Les appelants joueur (PvP via
     * grantVirtualCardsByName) l'ignorent simplement.</p>
     */
    @Transactional
    public String addVirtualCards(UUID userId, UUID cardId, int quantity) {
        validateQuantity(quantity);
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        Card card = findCollectibleCard(cardId);
        CardCollection collection = collectionRepository
                .findByUserIdAndCardIdAndCollectionType(userId, cardId, CardCollectionType.VIRTUAL)
                .orElse(null);
        int quantityBefore = collection == null ? 0 : collection.getQuantity();
        if (collection == null) {
            collection = new CardCollection();
            collection.setUser(user);
            collection.setCard(card);
            collection.setCollectionType(CardCollectionType.VIRTUAL);
            collection.setQuantity(quantity);
        } else {
            collection.setQuantity(collection.getQuantity() + quantity);
        }
        collectionRepository.save(collection);
        collectionRewardService.credit(userId, card, CardCollectionType.VIRTUAL, quantityBefore, collection.getQuantity());
        autoChallengeTriggerPublisher.publish(userId, AutoChallengeTrigger.CARD_OBTAINED);
        return card.getName();
    }

    /** Retire des cartes virtuelles et renvoie le nom de la carte touchee (voir addVirtualCards). */
    @Transactional
    public String removeVirtualCards(UUID userId, UUID cardId, int quantity) {
        validateQuantity(quantity);
        CardCollection collection = collectionRepository
                .findByUserIdAndCardIdAndCollectionType(userId, cardId, CardCollectionType.VIRTUAL)
                .orElseThrow(() -> new NotFoundException("Carte virtuelle introuvable dans le catalogue de l'utilisateur."));
        if (collection.getQuantity() < quantity) {
            throw new BadRequestException("Le retrait dépasse le nombre de cartes virtuelles détenues.");
        }
        String cardName = collection.getCard().getName();
        if (collection.getQuantity() == quantity) {
            collectionRepository.delete(collection);
            return cardName;
        }
        collection.setQuantity(collection.getQuantity() - quantity);
        collectionRepository.save(collection);
        return cardName;
    }

    // Used to unlock STS PvE cards for multiplayer use; matches by trading-card name since STS cards have no collection UUID of their own.
    @Transactional
    public void grantVirtualCardsByName(UUID userId, Collection<String> cardNames) {
        if (cardNames == null || cardNames.isEmpty()) {
            return;
        }
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));

        for (String cardName : cardNames) {
            if (cardName == null || cardName.isBlank()) {
                continue;
            }
            cardRepository.findByNameIgnoreCase(cardName.trim())
                    .filter(card -> !card.isDefaultCard())
                    .ifPresent(card -> addVirtualCards(userId, card.getId(), 1));
        }
    }

    private Card findCollectibleCard(UUID cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new NotFoundException("Carte introuvable."));
        if (card.isDefaultCard()) {
            throw new BadRequestException("La carte par défaut ne peut pas être attribuée.");
        }
        return card;
    }

    private void validateQuantity(int quantity) {
        if (quantity < 1 || quantity > 100) {
            throw new BadRequestException("La quantité doit être comprise entre 1 et 100.");
        }
    }

    private void updateDefaultFlag(Card card, boolean defaultCard) {
        if (card.isDefaultCard() && !defaultCard) {
            throw new BadRequestException("La carte par défaut ne peut pas perdre ce statut.");
        }

        if (!card.isDefaultCard() && defaultCard) {
            throw new BadRequestException("Une carte de collection ne peut pas devenir la carte par défaut.");
        }
    }

    private void rejectDefaultCardMetadataUpdate(
            String name,
            UUID categoryId,
            String category,
            Boolean enabled,
            Boolean defaultCard,
            Integer displayOrder,
            Integer firstAcquisitionPoints,
            Integer duplicatePoints,
            boolean clearFirstAcquisitionPoints,
            boolean clearDuplicatePoints
    ) {
        if (name != null || categoryId != null || category != null || enabled != null || defaultCard != null
                || displayOrder != null || firstAcquisitionPoints != null || duplicatePoints != null
                || clearFirstAcquisitionPoints || clearDuplicatePoints) {
            throw new BadRequestException("Seule l'image de la carte par défaut peut être modifiée.");
        }
    }

    private void applyImages(Card card, CardImageService.StoredCardImages images) {
        card.setImageObjectKey(images.imageObjectKey());
        card.setThumbnailObjectKey(images.thumbnailObjectKey());
        card.setImageUrl(images.imageUrl());
        card.setThumbnailUrl(images.thumbnailUrl());
        card.setContentType(images.contentType());
    }

    private void scheduleImageCleanup(String... objectKeys) {
        Runnable cleanup = () -> {
            for (String objectKey : objectKeys) {
                imageService.deleteQuietly(objectKey);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cleanup.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cleanup.run();
            }
        });
    }

    private CardDeckCardDto toDeckCard(Card card, CardCollection collection, Card defaultCard) {
        return toDeckCard(card, collection, defaultCard, collectionPointsCalculator.settings());
    }

    private CardDeckCardDto toDeckCard(
            Card card, CardCollection collection, Card defaultCard, CardCollectionSettings settings
    ) {
        return toDeckCard(card, collection, defaultCard, settings, false,
                goofyCardService.configuredCardId().orElse(null));
    }

    private CardDeckCardDto toDeckCard(
            Card card, CardCollection collection, Card defaultCard, CardCollectionSettings settings,
            boolean everythingOwned, UUID bonusCardId
    ) {
        int quantity = collection == null ? 0 : collection.getQuantity();
        if (card.isDefaultCard() || everythingOwned) {
            // Un exemplaire suffit à ouvrir la carte : le mode de test montre ce
            // qu'on peut jouer, il ne fabrique pas une collection à afficher.
            quantity = Math.max(1, quantity);
        }
        boolean owned = quantity > 0;
        Card imageSource = owned ? card : defaultCard;

        return new CardDeckCardDto(
                card.getId(),
                owned ? card.getName() : UNDISCOVERED_PLACEHOLDER,
                owned ? card.getCategoryId() : null,
                owned ? card.getCategory() : UNDISCOVERED_PLACEHOLDER,
                owned ? card.getTypeId() : null,
                owned ? card.getType() : null,
                card.isDefaultCard(),
                quantity,
                card.getDisplayOrder(),
                imageSource == null ? null : imageSource.getImageUrl(),
                imageSource == null ? null : imageSource.getThumbnailUrl(),
                owned ? card.getEffectiveFirstAcquisitionPoints() : null,
                owned ? card.getEffectiveDuplicatePoints() : null,
                owned ? card.getEffectiveDuplicatesPerPoint() : null,
                // La carte bonus est hors collection : elle ne vaut aucun point, donc
                // n'en affiche aucun et n'ajoute rien au score d'equipe.
                owned && collection != null && !card.getId().equals(bonusCardId)
                        ? collectionPointsCalculator.points(card, collection.getCollectionType(), quantity, settings)
                        : null
        );
    }

    private CardDto toDto(Card card) {
        return new CardDto(
                card.getId(),
                card.getName(),
                card.getCategoryId(),
                card.getCategory(),
                card.getTypeId(),
                card.getType(),
                card.isEnabled(),
                card.isDefaultCard(),
                card.getDisplayOrder(),
                card.getImageUrl(),
                card.getTextlessImageUrl(),
                card.getThumbnailUrl(),
                card.getContentType(),
                card.getFirstAcquisitionPoints(),
                card.getDuplicatePoints(),
                card.getEffectiveFirstAcquisitionPoints(),
                card.getEffectiveDuplicatePoints(),
                revealAnimation(card),
                card.getCreatedAt(),
                card.getUpdatedAt()
        );
    }

    /** La mise en scène de la catégorie, AUTO quand la carte n'en a pas. */
    private static fr.insastronaute.webapi.card.domain.CardRevealAnimation revealAnimation(Card card) {
        return card.getCategoryRef() == null || card.getCategoryRef().getRevealAnimation() == null
                ? fr.insastronaute.webapi.card.domain.CardRevealAnimation.AUTO
                : card.getCategoryRef().getRevealAnimation();
    }

    private Comparator<Card> cardComparator() {
        return Comparator
                .comparing(Card::getCategory, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(card -> !card.isDefaultCard())
                .thenComparing(Card::getDisplayOrder)
                .thenComparing(Card::getName, String.CASE_INSENSITIVE_ORDER);
    }

    private Comparator<DeckCard> deckCardComparator() {
        return Comparator
                // L'ordre des catégories est celui réglé en admin, pas l'alphabet :
                // trié par nom, « Légendaire » passait devant « Peu commune ».
                .comparing((DeckCard deckCard) -> categoryRank(deckCard.card()))
                .thenComparing(deckCard -> deckCard.card().getCategory(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(deckCard -> isOwned(deckCard.dto()), Comparator.reverseOrder())
                .thenComparing(deckCard -> !deckCard.dto().defaultCard())
                .thenComparing(deckCard -> deckCard.dto().displayOrder())
                .thenComparing(deckCard -> deckCard.dto().name(), String.CASE_INSENSITIVE_ORDER);
    }

    /**
     * Le rang d'affichage de la catégorie d'une carte.
     *
     * Une carte héritée peut ne porter que le nom de sa catégorie, sans lien vers
     * elle : faute de rang, elle passe après celles qui en ont un plutôt que de
     * s'intercaler à une place arbitraire.
     */
    private static int categoryRank(Card card) {
        return card.getCategoryRef() == null || card.getCategoryRef().getDisplayOrder() == null
                ? Integer.MAX_VALUE
                : card.getCategoryRef().getDisplayOrder();
    }

    private static boolean isOwned(CardDeckCardDto card) {
        return card.quantity() > 0;
    }

    /**
     * Cette carte compte-t-elle dans le total affiche ?
     *
     * <p>La carte bonus s'affiche une fois obtenue -- c'est une surprise, pas un
     * secret eternel -- mais n'entre ni dans la collection personnelle ni dans
     * celle de l'equipe. Elle sortirait sinon le compteur du joueur de ce que les
     * defis de completion mesurent.</p>
     */
    private static boolean isCounted(DeckCard deckCard, Optional<UUID> bonusCardId) {
        return bonusCardId.filter(deckCard.card().getId()::equals).isEmpty();
    }

    private static boolean isVisibleInDeck(
            Card card,
            Map<UUID, CardCollection> collectionByCardId,
            Optional<UUID> goofyCardId,
            boolean everythingOwned
    ) {
        return everythingOwned
                || goofyCardId.filter(card.getId()::equals).isEmpty()
                || collectionByCardId.containsKey(card.getId());
    }

    private record DeckCard(Card card, CardDeckCardDto dto) {
    }

    private CardCollectionType normalizeCollectionType(CardCollectionType collectionType) {
        return collectionType == null ? CardCollectionType.VIRTUAL : collectionType;
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Le nom de carte est requis.");
        }

        String normalized = name.trim();
        if (normalized.length() > 120) {
            throw new BadRequestException("Le nom de carte doit contenir au plus 120 caractères.");
        }
        return normalized;
    }

    private Integer optionalNonNegative(Integer value, String label) {
        if (value != null && value < 0) {
            throw new BadRequestException(label + " doivent être positifs ou nuls.");
        }
        return value;
    }

    private CardCategory resolveCategory(UUID categoryId, String categoryName) {
        if (categoryId != null) {
            return categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new NotFoundException("Catégorie de cartes introuvable."));
        }

        if (categoryName != null && !categoryName.isBlank()) {
            String normalized = CardCategoryService.normalizeName(categoryName);
            return categoryRepository.findByNameIgnoreCase(normalized)
                    .orElseThrow(() -> new NotFoundException("Catégorie de cartes introuvable."));
        }

        throw new BadRequestException("La catégorie de carte est requise.");
    }

    private CardType resolveType(UUID typeId) {
        if (typeId == null) {
            return null;
        }
        return typeRepository.findById(typeId)
                .orElseThrow(() -> new NotFoundException("Type de carte introuvable."));
    }

    private String typeGroupName(Card card) {
        return card.getType() == null ? "Sans type" : card.getType();
    }
}
