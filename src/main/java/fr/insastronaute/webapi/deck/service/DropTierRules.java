package fr.insastronaute.webapi.deck.service;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.deck.domain.DropTierRarity;
import fr.insastronaute.webapi.deck.dto.DropTierCreateRequest;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What makes a set of drop tiers coherent, independently of what holds them.
 *
 * A pack and a saved template answer to the same arithmetic — the chances add up
 * to a hundred, the guaranteed minimums fit inside one pull, the quotas leave
 * room to fill it — so the rules live in one place. A template that passed here
 * can always be poured into a pack; that is the whole point of saving one.
 */
public final class DropTierRules {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private DropTierRules() {
    }

    public static int resolveCardsPerPull(Integer cardsPerPull) {
        return cardsPerPull == null || cardsPerPull < 1 ? 1 : cardsPerPull;
    }

    public static int resolveMinCardsPerPull(Integer minCardsPerPull) {
        return minCardsPerPull == null || minCardsPerPull < 1 ? 0 : minCardsPerPull;
    }

    public static String normalizeLabel(String label) {
        return label == null || label.isBlank() ? null : label.trim();
    }

    public static void validateTiers(int cardsPerPull, List<DropTierCreateRequest> tiers) {
        BigDecimal totalChance = tiers.stream()
                .map(DropTierCreateRequest::dropChancePercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalChance.compareTo(ONE_HUNDRED) != 0) {
            throw new BadRequestException("La somme des pourcentages de drop doit être égale à 100.");
        }
        for (DropTierCreateRequest tier : tiers) {
            Set<UUID> uniqueCardIds = new HashSet<>(tier.cardIds());
            if (uniqueCardIds.size() != tier.cardIds().size()) {
                throw new BadRequestException("Un palier ne peut pas contenir deux fois la même carte.");
            }
            if (tier.maxCardsPerPull() != null
                    && resolveMinCardsPerPull(tier.minCardsPerPull()) > tier.maxCardsPerPull()) {
                throw new BadRequestException("Le minimum garanti d'un palier ne peut pas dépasser son quota maximum.");
            }
        }
        int guaranteedCards = tiers.stream()
                .map(DropTierCreateRequest::minCardsPerPull)
                .mapToInt(DropTierRules::resolveMinCardsPerPull)
                .sum();
        if (guaranteedCards > cardsPerPull) {
            throw new BadRequestException("Les minimums garantis dépassent le nombre de cartes par tirage.");
        }
        int cappedCapacity = tiers.stream()
                .map(DropTierCreateRequest::maxCardsPerPull)
                .mapToInt(maxCards -> maxCards == null ? cardsPerPull : maxCards)
                .sum();
        if (cappedCapacity < cardsPerPull) {
            throw new BadRequestException("Les quotas par palier ne permettent pas de tirer assez de cartes.");
        }
    }

    /**
     * La probabilité de voir ce palier au moins une fois en une ouverture.
     *
     * Un palier à minimum garanti tombe à chaque fois : sa probabilité vaut 1.
     * Les autres se disputent les places restantes, d'où {@code freeSlots} plutôt
     * que le nombre de cartes du pack.
     *
     * Le tirage réel est sans remise et borné par les quotas ; ce calcul les
     * ignore volontairement. Il sert à choisir une animation, pas à publier une
     * table de gains.
     */
    public static double pullProbability(double chancePercent, int freeSlots, Integer minCardsPerPull) {
        if (resolveMinCardsPerPull(minCardsPerPull) >= 1) {
            return 1.0;
        }
        double chance = Math.min(1.0, Math.max(0.0, chancePercent / 100.0));
        if (chance <= 0.0) {
            return 0.0;
        }
        int draws = Math.max(1, freeSlots);
        return 1.0 - Math.pow(1.0 - chance, draws);
    }

    /** Combien d'ouvertures, en moyenne, avant d'en voir un. */
    public static double expectedPullsForOne(double probability) {
        return probability <= 0.0 ? Double.POSITIVE_INFINITY : 1.0 / probability;
    }

    /**
     * Les seuils sont exprimés en ouvertures, pas en pourcentage : c'est la
     * grandeur que le joueur ressent, et la seule qui se compare d'un pack à
     * l'autre.
     */
    public static DropTierRarity rarityOf(double expectedPulls) {
        if (expectedPulls < 2) return DropTierRarity.COMMON;      // au moins un pack sur deux
        if (expectedPulls < 5) return DropTierRarity.UNCOMMON;
        if (expectedPulls < 15) return DropTierRarity.RARE;
        if (expectedPulls < 50) return DropTierRarity.EPIC;
        return DropTierRarity.LEGENDARY;
    }

    /**
     * Les places qu'un palier non garanti peut espérer : le pack moins ce que les
     * minimums garantis ont déjà réservé.
     */
    public static int freeSlots(int cardsPerPull, int guaranteedTotal) {
        return Math.max(1, cardsPerPull - Math.max(0, guaranteedTotal));
    }

    public static Map<UUID, Card> loadCards(CardRepository cardRepository, List<DropTierCreateRequest> tiers) {
        Set<UUID> requestedIds = tiers.stream()
                .flatMap(tier -> tier.cardIds().stream())
                .collect(Collectors.toSet());
        Map<UUID, Card> cardsById = cardRepository.findAllById(requestedIds).stream()
                .collect(Collectors.toMap(Card::getId, Function.identity()));
        if (cardsById.size() != requestedIds.size()) {
            throw new NotFoundException("Une ou plusieurs cartes sont introuvables.");
        }
        if (cardsById.values().stream().anyMatch(card -> !card.isEnabled() || card.isDefaultCard())) {
            throw new BadRequestException("Les paliers ne peuvent contenir que des cartes de collection activées.");
        }
        return cardsById;
    }
}
