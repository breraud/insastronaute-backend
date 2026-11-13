package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardSelectionSource;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.engine.GeneratedCardId;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.event.CardMoved;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Copies the card being played into one of its owner's piles.
 *
 * <p>Which pile is authored on the effect, as {@code cardSelectionSource}. This used to be
 * ignored and every copy landed in hand, which is not what the reference implementation does
 * (EffectResolver.AddCardToPile) and not what the cards were written against: a copy authored
 * into the discard pile is spent for the turn and comes back with the reshuffle, whereas the same
 * copy in hand can be replayed immediately. On a card that costs nothing, that difference is the
 * difference between an effect and an unbounded loop.
 */
public final class AddCopyOfCardHandler implements EffectHandler {

    private static final String NONE_PILE = "NONE";

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
            CardDefinition card, java.util.List<fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier> cardModifiers
    ) {
        int count = Math.max(1, effect.value());
        CardSelectionSource destination = destinationOf(effect);
        List<CardInstance> pile = new ArrayList<>(pileOf(target.piles(), destination));
        List<CombatEvent> events = new ArrayList<>();
        RandomState currentRandom = random;

        for (int i = 0; i < count; i++) {
            currentRandom = currentRandom.advance();
            CardInstance instance = new CardInstance(
                    GeneratedCardId.fromRandom(currentRandom), card.id());
            // Une copie glissée dans la pioche ou la défausse y prend une place au hasard ; en
            // main et à l'épuisement, elle se pose au bout comme le reste.
            int index;
            if (destination == CardSelectionSource.DRAW_PILE
                    || destination == CardSelectionSource.DISCARD_PILE) {
                fr.insastronaute.webapi.sts.combat.engine.PileOperations.Insertion insertion =
                        fr.insastronaute.webapi.sts.combat.engine.PileOperations.insertAtRandom(
                                pile, instance, currentRandom);
                currentRandom = insertion.random();
                index = insertion.index();
            } else {
                pile.add(instance);
                index = pile.size() - 1;
            }
            events.add(new CardMoved(
                    combatId,
                    revision,
                    target.combatantId(),
                    instance.instanceId(),
                    instance.definitionId(),
                    NONE_PILE,
                    pileName(destination),
                    index
            ));
        }

        CombatantState updated = new CombatantState(
                target.combatantId(),
                target.teamId(),
                target.controllerType(),
                target.maxHp(),
                target.hp(),
                target.armor(),
                target.energy(),
                target.baseEnergy(),
                target.turnDelayTicks(),
                withPile(target.piles(), destination, pile),
                target.statuses(),
                target.relics(),
                target.intentCardId(),
                target.patternIndex(), target.relicMemory());
        return new ResolutionStep(updated, timeline, events, currentRandom);
    }

    /**
     * The pile a copy goes to.
     *
     * <p>The unions -- {@code All}, {@code AllExceptExhaustPile} -- name somewhere to read cards
     * from, not somewhere to put one. Unity's resolver drops them into the hand along with an
     * unauthored source, and so does this.
     */
    private static CardSelectionSource destinationOf(EffectDefinition effect) {
        CardSelectionSource authored = effect.cardSelectionSource();
        if (authored == null) {
            return CardSelectionSource.HAND;
        }
        return switch (authored) {
            case HAND, DRAW_PILE, DISCARD_PILE, EXHAUST_PILE -> authored;
            case ALL, ALL_EXCEPT_EXHAUST_PILE -> CardSelectionSource.HAND;
        };
    }

    private static List<CardInstance> pileOf(CardPiles piles, CardSelectionSource destination) {
        return switch (destination) {
            case DRAW_PILE -> piles.draw();
            case DISCARD_PILE -> piles.discard();
            case EXHAUST_PILE -> piles.exhaust();
            default -> piles.hand();
        };
    }

    private static CardPiles withPile(
            CardPiles piles, CardSelectionSource destination, List<CardInstance> pile) {
        return switch (destination) {
            case DRAW_PILE -> new CardPiles(pile, piles.hand(), piles.discard(), piles.exhaust());
            case DISCARD_PILE -> new CardPiles(piles.draw(), piles.hand(), pile, piles.exhaust());
            case EXHAUST_PILE -> new CardPiles(piles.draw(), piles.hand(), piles.discard(), pile);
            default -> new CardPiles(piles.draw(), pile, piles.discard(), piles.exhaust());
        };
    }

    private static String pileName(CardSelectionSource destination) {
        return switch (destination) {
            case DRAW_PILE -> "DRAW";
            case DISCARD_PILE -> "DISCARD";
            case EXHAUST_PILE -> "EXHAUST";
            default -> "HAND";
        };
    }
}
