package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardModifier;
import fr.insastronaute.webapi.sts.combat.catalog.CardSelectionAction;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.engine.GeneratedCardId;
import fr.insastronaute.webapi.sts.combat.engine.selection.CardMerge;
import fr.insastronaute.webapi.sts.combat.engine.selection.CardSelectionCandidate;
import fr.insastronaute.webapi.sts.combat.engine.selection.CardSelectionResolver;
import fr.insastronaute.webapi.sts.combat.engine.selection.SelectionPile;
import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentCatalog;
import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentRoller;
import fr.insastronaute.webapi.sts.combat.event.CardEnchanted;
import fr.insastronaute.webapi.sts.combat.event.CardAltered;
import fr.insastronaute.webapi.sts.combat.event.CardMerged;
import fr.insastronaute.webapi.sts.combat.event.CardMoved;
import fr.insastronaute.webapi.sts.combat.event.CardTransformed;
import fr.insastronaute.webapi.sts.combat.event.CardSelectionResolved;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import fr.insastronaute.webapi.sts.combat.model.modifier.ModifierKind;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Moves the cards a player picked for a {@code CardSelection}.
 *
 * <p>The picks themselves are validated by the engine before any mutation, so this handler only
 * has to relocate already-approved instances and report the moves.
 */
public final class CardSelectionHandler implements EffectHandler {

    private final CardSelectionResolver resolver;

    public CardSelectionHandler(CardCatalog catalog) {
        this.resolver = new CardSelectionResolver(catalog);
    }

    public ResolutionStep handleSelection(
            UUID combatId,
            long revision,
            String sourceId,
            CombatantState target,
            EffectDefinition effect,
            RandomState random,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            CardDefinition card,
            List<StatModifier> cardModifiers,
            List<String> selectedCardInstanceIds
    ) {
        List<CardSelectionCandidate> candidates = resolver.candidates(effect, target.piles(), null);
        List<String> picks = selectedCardInstanceIds.isEmpty()
                ? serverPicks(effect, candidates)
                : selectedCardInstanceIds;
        if (picks.isEmpty()) {
            return new ResolutionStep(target, timeline, List.of(), random);
        }

        Map<SelectionPile, List<CardInstance>> piles = mutablePiles(target.piles());
        Map<String, CardSelectionCandidate> byInstanceId = new LinkedHashMap<>();
        candidates.forEach(candidate -> byInstanceId.put(candidate.instanceId(), candidate));

        List<CombatEvent> events = new ArrayList<>();
        RandomState currentRandom = random;

        if (effect.selectionAction() == CardSelectionAction.MERGE) {
            currentRandom = merge(combatId, revision, target.combatantId(),
                    picks, byInstanceId, piles, currentRandom, events);
            return finish(target, piles, timeline, events, currentRandom);
        }

        for (String selectedId : picks) {
            CardSelectionCandidate candidate = byInstanceId.get(selectedId);
            if (candidate == null) {
                throw new IllegalArgumentException("Selected card is not in the configured source");
            }
            if (effect.selectionAction() == CardSelectionAction.COPY_TO_HAND) {
                currentRandom = copyToHand(combatId, revision, target.combatantId(), candidate,
                        piles, currentRandom, events);
                continue;
            }
            if (effect.selectionAction() == CardSelectionAction.TRANSFORM) {
                currentRandom = transform(combatId, revision, target.combatantId(),
                        candidate, piles, currentRandom, events);
                continue;
            }
            if (effect.selectionAction() == CardSelectionAction.ENCHANT) {
                currentRandom = enchant(combatId, revision, target.combatantId(),
                        candidate, piles, currentRandom, events);
                continue;
            }
            CardInstance altered = alter(candidate.instance(), effect);
            if (altered != null) {
                List<CardInstance> pile = piles.get(candidate.pile());
                pile.set(pile.indexOf(candidate.instance()), altered);
                events.add(new CardAltered(combatId, revision, target.combatantId(),
                        selectedId, effect.selectionAction().jsonValue(),
                        candidate.pile().name()));
                continue;
            }
            SelectionPile destination = destinationOf(effect.selectionAction());
            piles.get(candidate.pile()).remove(candidate.instance());
            List<CardInstance> destinationPile = piles.get(destination);
            int position = destination == SelectionPile.DRAW ? 0 : destinationPile.size();
            destinationPile.add(position, candidate.instance());
            events.add(new CardMoved(combatId, revision, target.combatantId(), selectedId,
                    candidate.instance().definitionId(),
                    candidate.pile().name(), destination.name(), position));
        }
        events.add(new CardSelectionResolved(combatId, revision, target.combatantId(),
                effect.selectionAction().jsonValue(), picks));
        return finish(target, piles, timeline, events, currentRandom);
    }

    /**
     * Picks for a selection nobody answered.
     *
     * <p>An enemy move can force a selection on the player, and the server resolves it with no
     * player in the loop. Choosing the first eligible cards is arbitrary where Unity would ask,
     * but an effect that happens is closer to the game than one that silently does nothing.
     * Making the player answer needs the command protocol to carry a pending selection.
     */
    private static List<String> serverPicks(
            EffectDefinition effect,
            List<CardSelectionCandidate> candidates
    ) {
        int required = CardSelectionResolver.requiredCount(effect, candidates.size());
        return candidates.stream()
                .limit(required)
                .map(CardSelectionCandidate::instanceId)
                .toList();
    }

    private static ResolutionStep finish(
            CombatantState target,
            Map<SelectionPile, List<CardInstance>> piles,
            List<TimelineEntry> timeline,
            List<CombatEvent> events,
            RandomState random
    ) {
        CardPiles updatedPiles = new CardPiles(
                piles.get(SelectionPile.DRAW),
                piles.get(SelectionPile.HAND),
                piles.get(SelectionPile.DISCARD),
                piles.get(SelectionPile.EXHAUST));
        CombatantState updated = new CombatantState(
                target.combatantId(), target.teamId(), target.controllerType(), target.maxHp(), target.hp(),
                target.armor(), target.energy(), target.baseEnergy(), target.turnDelayTicks(), updatedPiles,
                target.statuses(), target.relics(), target.intentCardId(), target.patternIndex(), target.relicMemory());
        return new ResolutionStep(updated, timeline, events, random);
    }

    /**
     * Folds the selected cards into a single new card in hand.
     *
     * <p>The originals are gone, not moved, so the merged card is the only thing left of them.
     */
    private RandomState merge(
            UUID combatId,
            long revision,
            String combatantId,
            List<String> selectedCardInstanceIds,
            Map<String, CardSelectionCandidate> byInstanceId,
            Map<SelectionPile, List<CardInstance>> piles,
            RandomState random,
            List<CombatEvent> events
    ) {
        List<CardInstance> selected = new ArrayList<>();
        for (String selectedId : selectedCardInstanceIds) {
            CardSelectionCandidate candidate = byInstanceId.get(selectedId);
            if (candidate == null) {
                throw new IllegalArgumentException("Selected card is not in the configured source");
            }
            selected.add(candidate.instance());
            piles.get(candidate.pile()).remove(candidate.instance());
        }
        if (selected.isEmpty()) {
            return random;
        }

        RandomState next = random.advance();
        CardInstance merged = CardMerge.merge(
                GeneratedCardId.fromRandom(next), selected, resolver.catalog());
        if (merged == null) {
            return next;
        }
        List<CardInstance> hand = piles.get(SelectionPile.HAND);
        hand.add(merged);
        events.add(new CardMerged(combatId, revision, combatantId,
                selectedCardInstanceIds, merged.instanceId(), merged.definitionId(),
                merged.displayName()));
        return next;
    }
    /** Makes a distinct copy without moving or changing the selected source instance. */
    private static RandomState copyToHand(
        UUID combatId,
        long revision,
        String combatantId,
        CardSelectionCandidate candidate,
        Map<SelectionPile, List<CardInstance>> piles,
        RandomState random,
        List<CombatEvent> events
    ) {
    RandomState next = random.advance();
    CardInstance original = candidate.instance();
    CardInstance copy = new CardInstance(
        GeneratedCardId.fromRandom(next), original.definitionId(), original.enchantments(),
        original.displayName(), original.targetingMode(), original.tags(), original.modifiers(),
        original.addedEffects(), original.cardType(), original.cost());
    List<CardInstance> hand = piles.get(SelectionPile.HAND);
    hand.add(copy);
    events.add(new CardMoved(combatId, revision, combatantId, copy.instanceId(),
        copy.definitionId(), "NONE", SelectionPile.HAND.name(), hand.size() - 1));
    return next;
    }

    /**
     * Turns a card into a random one from the run's pool, in place.
     *
     * <p>The instance keeps its identity and its place in its pile, and loses everything that was
     * layered on it, so a card the player was tracking becomes a different card rather than
     * disappearing and reappearing elsewhere.
     */
    private RandomState transform(
            UUID combatId,
            long revision,
            String combatantId,
            CardSelectionCandidate candidate,
            Map<SelectionPile, List<CardInstance>> piles,
            RandomState random,
            List<CombatEvent> events
    ) {
        List<CardDefinition> pool = resolver.randomPool();
        if (pool.isEmpty()) {
            return random;
        }
        RandomState.RandomStep draw = random.nextInt(pool.size());
        CardInstance transformed =
                new CardInstance(candidate.instanceId(), pool.get(draw.value()).id());
        List<CardInstance> pile = piles.get(candidate.pile());
        pile.set(pile.indexOf(candidate.instance()), transformed);
        events.add(new CardTransformed(combatId, revision, combatantId, candidate.instanceId(),
                candidate.instance().definitionId(), transformed.definitionId(),
                candidate.pile().name()));
        return draw.next();
    }

    /**
     * Enchants a card where it lies, spending a rolled budget of charges on enchantments that fit
     * what the card actually does.
     */
    private RandomState enchant(
            UUID combatId,
            long revision,
            String combatantId,
            CardSelectionCandidate candidate,
            Map<SelectionPile, List<CardInstance>> piles,
            RandomState random,
            List<CombatEvent> events
    ) {
        CardDefinition definition = resolver.definitionOf(candidate.instance());
        if (definition == null) {
            return random;
        }
        EnchantmentRoller.Roll roll =
                EnchantmentRoller.roll(candidate.instance(), definition, random);
        if (roll.applied().isEmpty()) {
            return roll.random();
        }
        List<CardInstance> pile = piles.get(candidate.pile());
        pile.set(pile.indexOf(candidate.instance()), roll.instance());
        roll.applied().forEach(enchantment -> events.add(new CardEnchanted(
                combatId, revision, combatantId, candidate.instanceId(),
                enchantment.enchantmentId(), enchantment.level(),
                EnchantmentCatalog.levelOf(roll.instance(), enchantment.enchantmentId()),
                candidate.pile().name())));
        return roll.random();
    }

    /**
     * Changes a card in place, where the action reshapes the card rather than moving it.
     *
     * <p>{@code duration} carries how much, because the value is already spoken for by how many
     * cards the selection asks for.
     *
     * @return the altered card, or null when this action is not one of these
     */
    private static CardInstance alter(CardInstance instance, EffectDefinition effect) {
        return switch (effect.selectionAction()) {
            case REDUCE_COST -> instance.withModifier(costModifier(-1, false));
            case REDUCE_COST_TEMP -> instance.withModifier(costModifier(-1, true));
            case INCREASE_DAMAGE -> instance.withModifier(damageModifier(effect.duration(), false));
            case INCREASE_DAMAGE_TEMP -> instance.withModifier(damageModifier(effect.duration(), true));
            case ADD_DAMAGE_EFFECT -> instance
                    .withAddedEffect(new EffectDefinition(EffectType.DAMAGE, effect.duration()))
                    .withTargetingMode(TargetingMode.SINGLE_ENEMY);
            case ADD_ARMOR_EFFECT -> instance.withAddedEffect(
                    new EffectDefinition(EffectType.ARMOR, effect.duration(), true));
            case ADD_EXHAUST_TAG -> instance.withTag(CardTag.EXHAUST);
            case ADD_AUTOMATIC_TAG -> instance.withTag(CardTag.AUTOMATIC);
            case ADD_RETAIN_TAG -> instance.withTag(CardTag.RETAIN);
            case REMOVE_EXHAUST_TAG -> instance.withoutTag(CardTag.EXHAUST);
            case REMOVE_ETHEREAL_TAG -> instance.withoutTag(CardTag.ETHEREAL);
            case REMOVE_EXHAUST_AND_ETHEREAL_TAGS ->
                    instance.withoutTag(CardTag.EXHAUST).withoutTag(CardTag.ETHEREAL);
            case UNENCHANT -> instance.withEnchantments(List.of());
            default -> null;
        };
    }

    private static CardModifier costModifier(int amount, boolean temporary) {
        return new CardModifier(StatType.COST, ModifierKind.FLAT, amount, "", temporary);
    }

    private static CardModifier damageModifier(int amount, boolean temporary) {
        return new CardModifier(StatType.DAMAGE, ModifierKind.FLAT, amount, "", temporary);
    }

    private static Map<SelectionPile, List<CardInstance>> mutablePiles(CardPiles piles) {
        Map<SelectionPile, List<CardInstance>> mutable = new EnumMap<>(SelectionPile.class);
        for (SelectionPile pile : SelectionPile.values()) {
            mutable.put(pile, new ArrayList<>(pile.of(piles)));
        }
        return mutable;
    }

    private static SelectionPile destinationOf(CardSelectionAction action) {
        return switch (action) {
            case EXHAUST -> SelectionPile.EXHAUST;
            case DISCARD -> SelectionPile.DISCARD;
            case RETURN_TO_HAND -> SelectionPile.HAND;
            case TOP_OF_DRAW_PILE -> SelectionPile.DRAW;
            case CONSUME_AND_GAIN_ARMOR, CONSUME_AND_DEAL_DAMAGE_TO_ALL -> SelectionPile.EXHAUST;
            default -> throw new IllegalStateException(
                    "CardSelection action '" + action.jsonValue()
                            + "' does not move cards and is rejected before resolution");
        };
    }

    @Override
    public ResolutionStep handle(
            UUID combatId,
            long revision,
            String sourceId,
            CombatantState target,
            EffectDefinition effect,
            RandomState random,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            CardDefinition card,
            List<StatModifier> cardModifiers
    ) {
        throw new IllegalStateException(
                "CardSelection carries player picks and must be resolved through handleSelection");
    }
}
