package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardFilterTag;
import fr.insastronaute.webapi.sts.combat.catalog.CardSelectionSource;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.engine.GeneratedCardId;
import fr.insastronaute.webapi.sts.combat.engine.selection.SelectionPile;
import fr.insastronaute.webapi.sts.combat.event.CardMoved;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates cards drawn at random from the run's pool.
 *
 * <p>The effect's selection metadata is not decoration here: {@code cardSelectionSource} is the
 * pile the new cards land in, and {@code cardFilterTags} narrows the character's pool they are
 * drawn from.
 */
public final class AddRandomCardHandler implements EffectHandler {

    private static final String NO_PILE = "NONE";

    private final CardCatalog catalog;

    public AddRandomCardHandler(CardCatalog catalog) {
        this.catalog = catalog;
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
        int amount = Math.max(effect.value(), 0);
        List<CardDefinition> pool = pool(effect.cardFilterTags());
        if (amount == 0 || pool.isEmpty()) {
            return new ResolutionStep(target, timeline, List.of(), random);
        }

        SelectionPile destination = destinationOf(effect.cardSelectionSource());
        Map<SelectionPile, List<CardInstance>> piles = new EnumMap<>(SelectionPile.class);
        for (SelectionPile pile : SelectionPile.values()) {
            piles.put(pile, new ArrayList<>(pile.of(target.piles())));
        }
        List<CardInstance> destinationPile = piles.get(destination);
        List<CombatEvent> events = new ArrayList<>();
        RandomState currentRandom = random;

        for (int index = 0; index < amount; index++) {
            RandomState.RandomStep draw = currentRandom.nextInt(pool.size());
            currentRandom = draw.next();
            CardInstance created = new CardInstance(
                    GeneratedCardId.fromRandom(currentRandom), pool.get(draw.value()).id());
            // Dans la pioche ou la défausse, la carte créée prend une place au hasard : posée
            // dessous, elle n'arrivait qu'après tout le reste.
            int landedAt;
            if (destination == SelectionPile.DRAW || destination == SelectionPile.DISCARD) {
                fr.insastronaute.webapi.sts.combat.engine.PileOperations.Insertion insertion =
                        fr.insastronaute.webapi.sts.combat.engine.PileOperations.insertAtRandom(
                                destinationPile, created, currentRandom);
                currentRandom = insertion.random();
                landedAt = insertion.index();
            } else {
                destinationPile.add(created);
                landedAt = destinationPile.size() - 1;
            }
            events.add(new CardMoved(combatId, revision, target.combatantId(),
                    created.instanceId(), created.definitionId(), NO_PILE, destination.name(), landedAt));
        }

        CombatantState updated = new CombatantState(
                target.combatantId(), target.teamId(), target.controllerType(),
                target.maxHp(), target.hp(), target.armor(),
                target.energy(), target.baseEnergy(), target.turnDelayTicks(),
                new CardPiles(
                        piles.get(SelectionPile.DRAW),
                        piles.get(SelectionPile.HAND),
                        piles.get(SelectionPile.DISCARD),
                        piles.get(SelectionPile.EXHAUST)),
                target.statuses(), target.relics(),
                target.intentCardId(), target.patternIndex(), target.relicMemory());
        return new ResolutionStep(updated, timeline, events, currentRandom);
    }

    /** The run's character pool, narrowed by any OR-combined filters. */
    private List<CardDefinition> pool(List<CardFilterTag> filters) {
        List<CardDefinition> characterPool = catalog.randomPool();
        if (filters.isEmpty()) {
            return characterPool;
        }
        return characterPool.stream()
                .filter(candidate -> filters.stream().anyMatch(filter -> matches(candidate, filter)))
                .toList();
    }

    private static boolean matches(CardDefinition card, CardFilterTag filter) {
        return switch (filter) {
            case ATTACK -> card.type() == CardType.ATTACK;
            case SKILL -> card.type() == CardType.SKILL;
            case POWER -> card.type() == CardType.POWER;
            case RETAIN -> card.hasTag(CardTag.RETAIN);
            case ATOM -> card.hasTag(CardTag.ATOM);
            case MOLECULE -> card.hasTag(CardTag.MOLECULE);
            case NORM -> card.hasTag(CardTag.NORM);
            case COST_0 -> card.cost() == 0;
            case COST_1 -> card.cost() == 1;
            case COST_2 -> card.cost() == 2;
            case COST_3_PLUS -> card.cost() >= 3;
            case EXHAUST -> card.hasTag(CardTag.EXHAUST);
            case CREATED -> card.hasTag(CardTag.CREATED);
            case ETHEREAL -> card.hasTag(CardTag.ETHEREAL);
            // A definition has no enchantments and Unity has no branch for Exhausted, so these
            // filters accept nothing when the pool is made of definitions rather than instances.
            case UPGRADED, UNUPGRADED, EXHAUSTED -> false;
        };
    }

    private static SelectionPile destinationOf(CardSelectionSource source) {
        if (source == null) {
            return SelectionPile.HAND;
        }
        return switch (source) {
            case DRAW_PILE -> SelectionPile.DRAW;
            case DISCARD_PILE -> SelectionPile.DISCARD;
            case EXHAUST_PILE -> SelectionPile.EXHAUST;
            case HAND, ALL, ALL_EXCEPT_EXHAUST_PILE -> SelectionPile.HAND;
        };
    }
}
