package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.PileOperations;
import fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusOperations;
import fr.insastronaute.webapi.sts.combat.event.CardMoved;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.EnergyGained;
import fr.insastronaute.webapi.sts.combat.event.StatusApplied;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The handful of things a relic does to its owner, written once.
 *
 * <p>Each handler used to rebuild a whole {@code CombatantState} by hand to change one number,
 * which is where a forgotten field becomes a lost intent or a reset pattern.
 */
final class RelicActions {

    private static final String HAND_PILE = "HAND";
    private static final String NO_PILE = "NONE";

    private RelicActions() {
    }

    static ResolutionStep draw(
            CombatantState owner,
            int count,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            UUID combatId,
            long revision,
            List<CombatEvent> events
    ) {
        PileOperations.DrawResult drawn = PileOperations.draw(owner.piles(), count, random);
        List<CombatEvent> all = new ArrayList<>(events);
        List<CardInstance> hand = drawn.piles().hand();
        for (int index = owner.piles().hand().size(); index < hand.size(); index++) {
            all.add(new CardMoved(combatId, revision, owner.combatantId(),
                    hand.get(index).instanceId(), hand.get(index).definitionId(), NO_PILE, HAND_PILE, index));
        }
        return step(withPiles(owner, drawn.piles()), combatants, timeline, all, drawn.random());
    }

    /**
     * Puts a card the relic conjured straight into its owner's hand.
     *
     * <p>Unlike {@link #draw}, nothing leaves a pile: the card did not exist a moment ago, which
     * is why the move it announces comes from no pile at all.
     */
    static ResolutionStep addToHand(
            CombatantState owner,
            CardInstance card,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            UUID combatId,
            long revision,
            List<CombatEvent> events
    ) {
        List<CardInstance> hand = new ArrayList<>(owner.piles().hand());
        hand.add(card);
        fr.insastronaute.webapi.sts.combat.model.CardPiles piles =
                new fr.insastronaute.webapi.sts.combat.model.CardPiles(
                        owner.piles().draw(), hand, owner.piles().discard(), owner.piles().exhaust());

        List<CombatEvent> all = new ArrayList<>(events);
        // The card goes out with the move: it has never been in a pile the client could look in,
        // so the definition id alone would have it arrive under the name of the card it copies.
        all.add(new CardMoved(combatId, revision, owner.combatantId(), card.instanceId(),
                card.definitionId(), NO_PILE, HAND_PILE, hand.size() - 1, card));
        return step(withPiles(owner, piles), combatants, timeline, all, random);
    }

    static ResolutionStep gainEnergy(
            CombatantState owner,
            int amount,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            UUID combatId,
            long revision,
            List<CombatEvent> events
    ) {
        int energy = Math.max(0, owner.energy() + amount);
        List<CombatEvent> all = new ArrayList<>(events);
        all.add(new EnergyGained(combatId, revision, owner.combatantId(), amount, energy));
        return step(withEnergy(owner, energy), combatants, timeline, all, random);
    }

    static ResolutionStep grant(
            CombatantState owner,
            StatusInstance status,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            UUID combatId,
            long revision,
            List<CombatEvent> events
    ) {
        StatusOperations.Application application =
                StatusOperations.apply(owner.statuses(), status, owner.statuses());
        List<CombatEvent> all = new ArrayList<>(events);
        if (application.applied()) {
            all.add(new StatusApplied(combatId, revision, owner.combatantId(),
                    owner.combatantId(), status.statusType(), status.value(), status.duration(),
                    application.stacked()));
        }
        return step(withStatuses(owner, application.statuses()), combatants, timeline, all, random);
    }

    private static ResolutionStep step(
            CombatantState updated,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            List<CombatEvent> events,
            RandomState random
    ) {
        List<CombatantState> merged = new ArrayList<>(combatants);
        for (int index = 0; index < merged.size(); index++) {
            if (merged.get(index).combatantId().equals(updated.combatantId())) {
                merged.set(index, updated);
            }
        }
        return new ResolutionStep(merged, timeline, events, random);
    }

    private static CombatantState withPiles(
            CombatantState owner, fr.insastronaute.webapi.sts.combat.model.CardPiles piles) {
        return rebuild(owner, piles, owner.energy(), owner.statuses());
    }

    private static CombatantState withEnergy(CombatantState owner, int energy) {
        return rebuild(owner, owner.piles(), energy, owner.statuses());
    }

    private static CombatantState withStatuses(
            CombatantState owner, List<StatusInstance> statuses) {
        return rebuild(owner, owner.piles(), owner.energy(), statuses);
    }

    private static CombatantState rebuild(
            CombatantState owner,
            fr.insastronaute.webapi.sts.combat.model.CardPiles piles,
            int energy,
            List<StatusInstance> statuses
    ) {
        return new CombatantState(
                owner.combatantId(),
                owner.teamId(),
                owner.controllerType(),
                owner.maxHp(),
                owner.hp(),
                owner.armor(),
                energy,
                owner.baseEnergy(),
                owner.turnDelayTicks(),
                piles,
                statuses,
                owner.relics(),
                owner.intentCardId(),
                owner.patternIndex(), owner.relicMemory());
    }
}
