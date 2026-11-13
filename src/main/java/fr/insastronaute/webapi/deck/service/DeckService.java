package fr.insastronaute.webapi.deck.service;

import fr.insastronaute.webapi.card.service.CollectionRewardService;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardCollection;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.domain.CardRevealAnimation;
import fr.insastronaute.webapi.card.dto.CardDto;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.deck.domain.Deck;
import fr.insastronaute.webapi.deck.domain.DeckKind;
import fr.insastronaute.webapi.deck.domain.DropTier;
import fr.insastronaute.webapi.deck.dto.DeckCreateRequest;
import fr.insastronaute.webapi.deck.dto.DeckCategoryImageDto;
import fr.insastronaute.webapi.deck.dto.DeckDetailDto;
import fr.insastronaute.webapi.deck.dto.DeckSummaryDto;
import fr.insastronaute.webapi.deck.dto.DropTierCreateRequest;
import fr.insastronaute.webapi.deck.dto.DropTierDto;
import fr.insastronaute.webapi.deck.dto.DropTierOrderUpdateRequest;
import fr.insastronaute.webapi.deck.dto.PullDropDto;
import fr.insastronaute.webapi.deck.dto.PullResultDto;
import fr.insastronaute.webapi.deck.repository.DeckRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DeckService {

    private fr.insastronaute.webapi.economy.service.CurrencyLedger currencyLedger =
            fr.insastronaute.webapi.economy.service.CurrencyLedger.NOOP;

    /** Par setter : voir CurrencyLedger. Spring refuse de demarrer sans implementation. */
    @org.springframework.beans.factory.annotation.Autowired
    void setCurrencyLedger(fr.insastronaute.webapi.economy.service.CurrencyLedger currencyLedger) {
        this.currencyLedger = currencyLedger;
    }

    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final CardCollectionRepository collectionRepository;
    private final DeckImageService imageService;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final Random random;
    private final AutoChallengeTriggerPublisher autoChallengeTriggerPublisher;
    private final CollectionRewardService collectionRewardService;

    public DeckService(
            DeckRepository deckRepository,
            CardRepository cardRepository,
            UserRepository userRepository,
            CardCollectionRepository collectionRepository,
            DeckImageService imageService,
            ObjectMapper objectMapper,
            Validator validator,
            AutoChallengeTriggerPublisher autoChallengeTriggerPublisher,
            CollectionRewardService collectionRewardService
    ) {
        this.deckRepository = deckRepository;
        this.cardRepository = cardRepository;
        this.userRepository = userRepository;
        this.collectionRepository = collectionRepository;
        this.imageService = imageService;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.autoChallengeTriggerPublisher = autoChallengeTriggerPublisher;
        this.collectionRewardService = collectionRewardService;
        this.random = new SecureRandom();
    }

    @Transactional
    public DeckDetailDto create(String deckData, MultipartFile image) {
        DeckCreateRequest request = parseCreateRequest(deckData);
        validateCreateRequest(request, image);

        Deck deck = new Deck();
        deck.setId(UUID.randomUUID());
        deck.setName(request.name().trim());
        deck.setPrice(request.price());
        deck.setCardsPerPull(resolveCardsPerPull(request.cardsPerPull()));
        deck.setKind(resolveKind(request.kind()));
        deck.setFrontXPercent(resolveFrontXPercent(request.frontXPercent()));
        deck.setFrontZoom(resolveFrontZoom(request.frontZoom()));
        deck.setImageUrl(imageService.store(deck.getId(), image));

        deck.setDropTiers(toDropTiers(request));

        return toDetailDto(deckRepository.save(deck), true);
    }

    @Transactional
    public DeckDetailDto update(UUID deckId, String deckData, MultipartFile image) {
        DeckCreateRequest request = parseCreateRequest(deckData);
        validateUpdateRequest(request);
        Deck deck = deckRepository.findWithDropTiersById(deckId)
                .orElseThrow(() -> new NotFoundException("Deck introuvable."));

        deck.setName(request.name().trim());
        deck.setPrice(request.price());
        deck.setCardsPerPull(resolveCardsPerPull(request.cardsPerPull()));
        deck.setKind(resolveKind(request.kind()));
        deck.setFrontXPercent(resolveFrontXPercent(request.frontXPercent()));
        deck.setFrontZoom(resolveFrontZoom(request.frontZoom()));
        if (image != null && !image.isEmpty()) {
            deck.setImageUrl(imageService.store(deck.getId(), image));
        }
        deck.setDropTiers(toDropTiers(request));

        return toDetailDto(deckRepository.save(deck), true);
    }

    /**
     * Supprime un pack et ses paliers.
     *
     * Rien d'autre en base ne désigne un pack : une carte tirée rejoint la
     * collection sans garder trace du pack d'où elle vient, et les modèles de
     * paliers vivent à côté. La suppression ne retire donc aucune carte à
     * personne — seulement l'offre en boutique.
     */
    @Transactional
    public void delete(UUID deckId) {
        Deck deck = deckRepository.findWithDropTiersById(deckId)
                .orElseThrow(() -> new NotFoundException("Deck introuvable."));
        deckRepository.delete(deck);
        imageService.deleteAll(deckId);
    }

    @Transactional(readOnly = true)
    public List<DeckSummaryDto> findAll() {
        return deckRepository.findAll().stream()
                .map(this::toSummaryDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public DeckDetailDto findById(UUID deckId) {
        Deck deck = deckRepository.findWithDropTiersById(deckId)
                .orElseThrow(() -> new NotFoundException("Deck introuvable."));
        return toDetailDto(deck, false);
    }

    @Transactional
    public DeckDetailDto reorderDropTiers(UUID deckId, DropTierOrderUpdateRequest request) {
        if (request == null || request.tierIds() == null || request.tierIds().isEmpty()) {
            throw new BadRequestException("La liste des paliers est requise.");
        }

        Deck deck = deckRepository.findWithDropTiersById(deckId)
                .orElseThrow(() -> new NotFoundException("Deck introuvable."));
        Set<UUID> existingIds = deck.getDropTiers().stream().map(DropTier::getId).collect(Collectors.toSet());
        Set<UUID> requestedIds = new HashSet<>(request.tierIds());
        if (requestedIds.size() != request.tierIds().size() || !existingIds.equals(requestedIds)) {
            throw new BadRequestException("La liste doit contenir exactement les paliers de ce pack.");
        }

        Map<UUID, DropTier> tiersById = deck.getDropTiers().stream()
                .collect(Collectors.toMap(DropTier::getId, Function.identity()));
        for (int index = 0; index < request.tierIds().size(); index++) {
            tiersById.get(request.tierIds().get(index)).setDisplayOrder(index);
        }

        deckRepository.save(deck);
        return toDetailDto(deck, true);
    }

    @Transactional
    public PullResultDto pull(UUID deckId, String keycloakSub) {
        AppUser user = userRepository.findWithLockByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        Deck deck = deckRepository.findWithDropTiersById(deckId)
                .orElseThrow(() -> new NotFoundException("Deck introuvable."));

        long balance = user.getTokens();
        if (balance < deck.getPrice()) {
            throw new BadRequestException("Solde insuffisant.");
        }

        user.setTokens(balance - deck.getPrice());
        currencyLedger.record(user, fr.insastronaute.webapi.economy.domain.CurrencySource.PACK_PURCHASE, -deck.getPrice(), 0, deck.getId());
        List<TierDrop> drops = drawCards(deck);
        for (TierDrop drop : drops) {
            grantCard(user, drop.card());
        }

        return new PullResultDto(
                deck.getId(),
                user.getTokens(),
                drops.stream()
                        .map(drop -> toPullDropDto(deck, drop))
                        .toList()
        );
    }

    /**
     * L'animation de révélation se joue sur la rareté du palier, pas sur son
     * pourcentage : dix pour cent d'un pack à dix cartes se voient à presque
     * chaque ouverture, dix pour cent d'un pack à une carte se font attendre.
     */
    private PullDropDto toPullDropDto(Deck deck, TierDrop drop) {
        double probability = DropTierRules.pullProbability(
                drop.tier().getDropChancePercent() == null ? 0 : drop.tier().getDropChancePercent().doubleValue(),
                freeSlots(deck),
                drop.tier().getMinCardsPerPull()
        );
        double expectedPulls = DropTierRules.expectedPullsForOne(probability);
        return new PullDropDto(
                drop.tier().getId(),
                drop.tier().getLabel(),
                toCardDto(drop.card()),
                DropTierRules.rarityOf(expectedPulls),
                Double.isInfinite(expectedPulls) ? null : round(expectedPulls)
        );
    }

    private DeckCreateRequest parseCreateRequest(String deckData) {
        if (deckData == null || deckData.isBlank()) {
            throw new BadRequestException("Les données du deck sont requises.");
        }
        try {
            return objectMapper.readValue(deckData, DeckCreateRequest.class);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Le JSON deckData est invalide.");
        }
    }

    private DeckKind resolveKind(DeckKind kind) {
        return kind == null ? DeckKind.PACK : kind;
    }

    /**
     * A single card is still a deck, so nothing stops an admin from sending several
     * tiers or several cards. The shop would then show one card and hand out another,
     * which is why the shape is pinned down here rather than trusted.
     */
    private void validateSingleCardShape(DeckCreateRequest request) {
        if (request.dropTiers().size() != 1) {
            throw new BadRequestException("Une carte à l'unité ne peut avoir qu'un seul palier.");
        }
        if (request.dropTiers().getFirst().cardIds().size() != 1) {
            throw new BadRequestException("Une carte à l'unité doit contenir exactement une carte.");
        }
        if (resolveCardsPerPull(request.cardsPerPull()) != 1) {
            throw new BadRequestException("Une carte à l'unité doit se vendre par une.");
        }
    }

    private void validateCreateRequest(DeckCreateRequest request, MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BadRequestException("L'image du deck est requise.");
        }
        validateUpdateRequest(request);
    }

    private void validateUpdateRequest(DeckCreateRequest request) {
        Set<ConstraintViolation<DeckCreateRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .findFirst()
                    .map(ConstraintViolation::getMessage)
                    .orElse("Les données du deck sont invalides.");
            throw new BadRequestException(message);
        }
        // Un pack et un modèle enregistré répondent à la même arithmétique : elle
        // vit dans DropTierRules pour qu'un modèle valide soit toujours versable
        // dans un pack.
        DropTierRules.validateTiers(resolveCardsPerPull(request.cardsPerPull()), request.dropTiers());
        if (resolveKind(request.kind()) == DeckKind.CARD) {
            validateSingleCardShape(request);
        }
    }

    private Map<UUID, Card> loadCardsById(DeckCreateRequest request) {
        return DropTierRules.loadCards(cardRepository, request.dropTiers());
    }

    private List<DropTier> toDropTiers(DeckCreateRequest request) {
        Map<UUID, Card> cardsById = loadCardsById(request);
        List<DropTier> tiers = new ArrayList<>();
        for (int index = 0; index < request.dropTiers().size(); index++) {
            DropTierCreateRequest tierRequest = request.dropTiers().get(index);
            DropTier tier = new DropTier();
            tier.setLabel(normalizeLabel(tierRequest.label()));
            tier.setDropChancePercent(tierRequest.dropChancePercent());
            tier.setMaxCardsPerPull(tierRequest.maxCardsPerPull());
            tier.setMinCardsPerPull(tierRequest.minCardsPerPull());
            tier.setDisplayOrder(index);
            tier.setCards(tierRequest.cardIds().stream().map(cardsById::get).toList());
            tiers.add(tier);
        }
        return tiers;
    }

    private List<TierDrop> drawCards(Deck deck) {
        List<TierDrop> drops = new ArrayList<>();
        Map<UUID, Integer> tierCounts = new HashMap<>();

        for (DropTier tier : deck.getDropTiers()) {
            if (visibleCards(tier).findAny().isEmpty()) {
                continue;
            }
            for (int index = 0; index < resolveMinCardsPerPull(tier.getMinCardsPerPull()); index++) {
                addDrop(drops, tierCounts, tier);
            }
        }
        for (int index = drops.size(); index < deck.getCardsPerPull(); index++) {
            DropTier selectedTier = selectTier(deck.getDropTiers(), tierCounts);
            addDrop(drops, tierCounts, selectedTier);
        }
        return drops;
    }

    private void addDrop(List<TierDrop> drops, Map<UUID, Integer> tierCounts, DropTier tier) {
        Card selectedCard = selectCard(tier);
        drops.add(new TierDrop(tier, selectedCard));
        tierCounts.merge(tier.getId(), 1, Integer::sum);
    }

    private DropTier selectTier(List<DropTier> dropTiers, Map<UUID, Integer> tierCounts) {
        List<DropTier> eligibleTiers = dropTiers == null ? List.of() : dropTiers.stream()
                .filter(tier -> !hasReachedPullQuota(tier, tierCounts))
                .filter(tier -> visibleCards(tier).findAny().isPresent())
                .toList();
        if (eligibleTiers.isEmpty()) {
            throw new BadRequestException("Ce deck ne contient aucun palier de drop.");
        }

        double totalWeight = eligibleTiers.stream()
                .mapToDouble(tier -> tier.getDropChancePercent().doubleValue())
                .sum();
        double remaining = random.nextDouble(totalWeight);
        DropTier fallback = eligibleTiers.getLast();
        for (DropTier tier : eligibleTiers) {
            remaining -= tier.getDropChancePercent().doubleValue();
            if (remaining < 0.0) {
                return tier;
            }
        }
        return fallback;
    }

    private Card selectCard(DropTier tier) {
        List<Card> cards = visibleCards(tier).toList();
        if (cards.isEmpty()) {
            throw new BadRequestException("Le palier sélectionné ne contient aucune carte.");
        }
        return cards.get(random.nextInt(cards.size()));
    }

    private java.util.stream.Stream<Card> visibleCards(DropTier tier) {
        List<Card> cards = tier.getCards();
        if (cards == null || cards.isEmpty()) {
            return java.util.stream.Stream.empty();
        }
        return cards.stream().filter(this::isPublicCard);
    }

    private boolean hasReachedPullQuota(DropTier tier, Map<UUID, Integer> tierCounts) {
        Integer maxCardsPerPull = tier.getMaxCardsPerPull();
        return maxCardsPerPull != null && tierCounts.getOrDefault(tier.getId(), 0) >= maxCardsPerPull;
    }

    private void grantCard(AppUser user, Card card) {
        CardCollection collection = collectionRepository
                .findByUserIdAndCardIdAndCollectionType(user.getId(), card.getId(), CardCollectionType.VIRTUAL)
                .orElse(null);
        int quantityBefore = collection == null ? 0 : collection.getQuantity();
        if (collection == null) {
            collection = new CardCollection();
            collection.setUser(user);
            collection.setCard(card);
            collection.setCollectionType(CardCollectionType.VIRTUAL);
            collection.setQuantity(1);
        } else {
            collection.setQuantity(collection.getQuantity() + 1);
        }
        collectionRepository.save(collection);
        collectionRewardService.credit(user.getId(), card, CardCollectionType.VIRTUAL, quantityBefore, collection.getQuantity());
        autoChallengeTriggerPublisher.publish(user.getId(), AutoChallengeTrigger.CARD_OBTAINED);
    }

    /** Hors de [0, 100], le recto sortirait de l'image : on ramène au bord. */
    private static double resolveFrontXPercent(Double requested) {
        if (requested == null || requested.isNaN()) return 50.0;
        return Math.clamp(requested, 0.0, 100.0);
    }

    /** Réduire n'a pas de sens (l'emballage ne remplirait plus le cadre), et
     *  au-delà de 4 il ne reste qu'une bouillie de pixels. */
    private static double resolveFrontZoom(Double requested) {
        if (requested == null || requested.isNaN()) return 1.0;
        return Math.clamp(requested, 1.0, 4.0);
    }

    private DeckSummaryDto toSummaryDto(Deck deck) {
        return new DeckSummaryDto(
                deck.getId(),
                deck.getName(),
                deck.getPrice(),
                deck.getCardsPerPull(),
                deckImageUrl(deck),
                deck.getFrontXPercent(),
                deck.getFrontZoom(),
                deck.getKind()
        );
    }

    private DeckDetailDto toDetailDto(Deck deck, boolean includeHiddenCategories) {
        return new DeckDetailDto(
                deck.getId(),
                deck.getName(),
                deck.getPrice(),
                deck.getCardsPerPull(),
                deckImageUrl(deck),
                deck.getFrontXPercent(),
                deck.getFrontZoom(),
                deck.getKind(),
                toCategoryImageDtos(deck, includeHiddenCategories),
                deck.getDropTiers().stream()
                        .sorted(Comparator.comparing(DropTier::getDisplayOrder))
                        .filter(tier -> includeHiddenCategories || visibleCards(tier).findAny().isPresent())
                        .map(tier -> toDropTierDto(tier, deck, includeHiddenCategories))
                        .toList()
        );
    }

    private String deckImageUrl(Deck deck) {
        return imageService.publicUrlForStoredValue(deck.getImageUrl());
    }

    private DropTierDto toDropTierDto(DropTier tier, Deck deck, boolean includeHiddenCategories) {
        // La rareté ressentie se calcule ici, une fois : la boutique, l'animation
        // de révélation et le réglage admin doivent lire le même chiffre.
        double probability = DropTierRules.pullProbability(
                tier.getDropChancePercent() == null ? 0 : tier.getDropChancePercent().doubleValue(),
                freeSlots(deck),
                tier.getMinCardsPerPull()
        );
        double expectedPulls = DropTierRules.expectedPullsForOne(probability);
        return new DropTierDto(
                tier.getId(),
                tier.getLabel(),
                tier.getDropChancePercent(),
                tier.getMaxCardsPerPull(),
                tier.getMinCardsPerPull(),
                tier.getCards().stream()
                        .filter(card -> includeHiddenCategories || isPublicCard(card))
                        .map(this::toCardDto)
                        .toList(),
                round(probability * 100),
                Double.isInfinite(expectedPulls) ? null : round(expectedPulls),
                DropTierRules.rarityOf(expectedPulls)
        );
    }

    /** Ce qu'il reste à tirer une fois les minimums garantis servis. */
    private static int freeSlots(Deck deck) {
        int guaranteed = deck.getDropTiers().stream()
                .map(DropTier::getMinCardsPerPull)
                .mapToInt(DropTierRules::resolveMinCardsPerPull)
                .sum();
        return DropTierRules.freeSlots(deck.getCardsPerPull() == null ? 1 : deck.getCardsPerPull(), guaranteed);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private List<DeckCategoryImageDto> toCategoryImageDtos(Deck deck, boolean includeHiddenCategories) {
        Map<String, DeckCategoryImageDto> categoriesByKey = new LinkedHashMap<>();
        deck.getDropTiers().stream()
                .sorted(Comparator.comparing(DropTier::getDisplayOrder))
                .flatMap(tier -> tier.getCards().stream())
                .filter(card -> includeHiddenCategories || isPublicCard(card))
                // Même règle que la collection : l'ordre des catégories est celui
                // réglé en admin. Trié par nom, « Légendaire » passait devant
                // « Peu commune » sur la vignette du pack aussi.
                .sorted(Comparator
                        .comparing(DeckService::categoryRank)
                        .thenComparing(Card::getCategory, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Card::getDisplayOrder)
                        .thenComparing(Card::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(card -> categoriesByKey.putIfAbsent(categoryKey(card), new DeckCategoryImageDto(
                        card.getCategoryId(),
                        card.getCategory(),
                        card.getCategoryRef() == null ? null : card.getCategoryRef().getImageUrl(),
                        card.getCategoryRef() == null ? null : card.getCategoryRef().getThumbnailUrl()
                )));
        return List.copyOf(categoriesByKey.values());
    }

    /** Une carte sans lien vers sa catégorie n'a pas de rang : elle passe après. */
    private static int categoryRank(Card card) {
        return card.getCategoryRef() == null || card.getCategoryRef().getDisplayOrder() == null
                ? Integer.MAX_VALUE
                : card.getCategoryRef().getDisplayOrder();
    }

    private boolean isPublicCard(Card card) {
        return card.getCategoryRef() == null
                || (!card.getCategoryRef().isHidden() && !card.getCategoryRef().isGame());
    }

    private String categoryKey(Card card) {
        UUID categoryId = card.getCategoryId();
        return categoryId == null ? card.getCategory().toLowerCase(Locale.ROOT) : categoryId.toString();
    }

    private CardDto toCardDto(Card card) {
        return new CardDto(
                card.getId(),
                card.getName(),
                card.getCategoryId(),
                card.getCategory(),
                null,
                null,
                card.isEnabled(),
                card.isDefaultCard(),
                card.getDisplayOrder(),
                card.getImageUrl(),
                card.getTextlessImageUrl(),
                card.getThumbnailUrl(),
                card.getContentType(),
                null,
                null,
                0,
                0,
                // C'est la catégorie qui décide de la mise en scène : une même
                // catégorie doit se révéler pareil quel que soit le pack.
                card.getCategoryRef() == null || card.getCategoryRef().getRevealAnimation() == null
                        ? CardRevealAnimation.AUTO
                        : card.getCategoryRef().getRevealAnimation(),
                card.getCreatedAt(),
                card.getUpdatedAt()
        );
    }

    private String normalizeLabel(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        return label.trim();
    }

    private int resolveCardsPerPull(Integer cardsPerPull) {
        return cardsPerPull == null ? 1 : cardsPerPull;
    }

    private int resolveMinCardsPerPull(Integer minCardsPerPull) {
        return minCardsPerPull == null ? 0 : minCardsPerPull;
    }

    private record TierDrop(DropTier tier, Card card) {
    }
}
