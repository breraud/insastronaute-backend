package fr.insastronaute.webapi.sts.combat.engine.selection;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardFilterTag;
import fr.insastronaute.webapi.sts.combat.catalog.CardSelectionSource;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Turns a {@code CardSelection} effect into the exact list of instances a player may pick.
 *
 * <p>Command validation and effect resolution both go through this one view, so what the engine
 * accepts and what it then moves can never diverge. The view is built once, before any mutation,
 * which is what lets a rejected selection leave the state untouched.
 *
 * <p>Candidate order is the pile order, and piles are visited in a fixed order, so the candidate
 * list of a given state is always the same list.
 */
public final class CardSelectionResolver {

    /**
     * Order the piles are concatenated in for a combined source. It fixes which cards a capped
     * selection offers first, so it follows Unity's concatenation exactly.
     */
    private static final List<SelectionPile> COMBINED_ORDER =
            List.of(SelectionPile.HAND, SelectionPile.DISCARD, SelectionPile.DRAW, SelectionPile.EXHAUST);

    private final CardCatalog catalog;

    public CardSelectionResolver(CardCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    /** @see CardCatalog#randomPool() */
    public List<CardDefinition> randomPool() {
        return catalog.randomPool();
    }

    public CardCatalog catalog() {
        return catalog;
    }

    /** @return the definition behind an instance, or null when the catalog does not hold it */
    public CardDefinition definitionOf(CardInstance instance) {
        return catalog.cards().get(instance.definitionId());
    }

    /**
     * @param piles           the actor's piles, as they stand before the selection resolves
     * @param excludedCardInstanceId the instance being played, which never selects itself, or
     *                               {@code null} when the caller already removed it
     */
    public List<CardSelectionCandidate> candidates(
            EffectDefinition effect,
            CardPiles piles,
            String excludedCardInstanceId
    ) {
        Set<SelectionPile> sources = pilesOf(effect.cardSelectionSource());
        List<CardSelectionCandidate> candidates = new ArrayList<>();
        for (SelectionPile pile : COMBINED_ORDER) {
            if (!sources.contains(pile)) {
                continue;
            }
            for (CardInstance instance : pile.of(piles)) {
                if (instance.instanceId().equals(excludedCardInstanceId)) {
                    continue;
                }
                if (matches(instance, effect.cardFilterTags())) {
                    candidates.add(new CardSelectionCandidate(instance, pile));
                }
            }
        }
        return List.copyOf(candidates);
    }

    /**
     * How many cards this selection asks for, given what is actually available.
     *
     * <p>Unity's {@code -1} means "every eligible card". A positive amount is capped by the
     * candidate count, because a selection cannot demand cards that are not there.
     */
    public static int requiredCount(EffectDefinition effect, int candidateCount) {
        return effect.value() < 0 ? candidateCount : Math.min(effect.value(), candidateCount);
    }

    private static Set<SelectionPile> pilesOf(CardSelectionSource source) {
        if (source == null) {
            return Set.of();
        }
        return switch (source) {
            case HAND -> Set.of(SelectionPile.HAND);
            case DRAW_PILE -> Set.of(SelectionPile.DRAW);
            case DISCARD_PILE -> Set.of(SelectionPile.DISCARD);
            case EXHAUST_PILE -> Set.of(SelectionPile.EXHAUST);
            case ALL -> Set.of(
                    SelectionPile.HAND, SelectionPile.DRAW, SelectionPile.DISCARD, SelectionPile.EXHAUST);
            case ALL_EXCEPT_EXHAUST_PILE -> Set.of(
                    SelectionPile.HAND, SelectionPile.DRAW, SelectionPile.DISCARD);
        };
    }

    /**
     * Filters combine with OR: a card qualifies as soon as one authored filter accepts it. That
     * is what makes a list such as {@code [Attack, Skill]} mean "attacks and skills" rather than
     * the empty set a conjunction would produce.
     */
    private boolean matches(CardInstance instance, List<CardFilterTag> filters) {
        if (filters.isEmpty()) {
            return true;
        }
        CardDefinition authored = catalog.cards().get(instance.definitionId());
        if (authored == null) {
            return false;
        }
        // Judged on the card as it now stands, not as it was authored: a merged card carries its
        // parts' markers, a conjured copy carries its own family and price, and a filter that
        // read only the definition would offer the player a different list from the one the
        // client offers -- which is a rejected selection, not a wrong card.
        CardDefinition definition = fr.insastronaute.webapi.sts.combat.engine.EffectiveCard.of(
                instance, authored);
        for (CardFilterTag filter : filters) {
            if (matches(instance, definition, filter)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(
            CardInstance instance,
            CardDefinition definition,
            CardFilterTag filter
    ) {
        return switch (filter) {
            case ATTACK -> definition.type() == CardType.ATTACK;
            case SKILL -> definition.type() == CardType.SKILL;
            case POWER -> definition.type() == CardType.POWER;
            case RETAIN -> definition.hasTag(CardTag.RETAIN);
            case ATOM -> definition.hasTag(CardTag.ATOM);
            case MOLECULE -> definition.hasTag(CardTag.MOLECULE);
            case NORM -> definition.hasTag(CardTag.NORM);
            case UPGRADED -> !instance.enchantments().isEmpty();
            case UNUPGRADED -> instance.enchantments().isEmpty();
            case COST_0 -> definition.cost() == 0;
            case COST_1 -> definition.cost() == 1;
            case COST_2 -> definition.cost() == 2;
            case COST_3_PLUS -> definition.cost() >= 3;
            case EXHAUST -> definition.hasTag(CardTag.EXHAUST);
            case CREATED -> definition.hasTag(CardTag.CREATED);
            case ETHEREAL -> definition.hasTag(CardTag.ETHEREAL);
            // Unity's selection predicate has no branch for Exhausted, so it accepts nothing.
            case EXHAUSTED -> false;
        };
    }

    /** Candidate list rendered for a rejection message, so a client can see what was eligible. */
    public static String describe(List<CardSelectionCandidate> candidates) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        candidates.forEach(candidate -> ids.add(candidate.instanceId()));
        return String.join(", ", ids);
    }
}
