package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.engine.BattleCalculator;
import fr.insastronaute.webapi.sts.combat.engine.EffectContext;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Places one combatant's next turn right after another's.
 *
 * <p>Unchecked, the target cuts in: "Placez le prochain tour de la cible juste après le vôtre",
 * so the target's next turn comes right after the turn under way, ahead of everybody else's.
 * With {@code targetSelf} it is the other way round: "Placez votre prochain tour juste après
 * celui de la cible" -- the caster's next turn comes right after the target's. The target is the
 * card's in both cases ({@code EffectResolver.targetsOf} keeps it for this effect).
 *
 * <p>The effect moves a turn a combatant already owns; it never adds an entry, since the engine
 * refuses a timeline holding two entries for one combatant.
 */
public final class CutInTurnHandler implements EffectHandler {

    private final fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine relicEngine;

    public CutInTurnHandler(fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine relicEngine) {
        this.relicEngine = relicEngine;
    }

    public CutInTurnHandler() {
        this(null);
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
            CardDefinition card, java.util.List<fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier> cardModifiers
    ) {
        String moverId = effect.targetSelf() ? sourceId : target.combatantId();
        String anchorId = effect.targetSelf() ? target.combatantId() : sourceId;
        ResolutionStep unchanged = new ResolutionStep(target, timeline, List.of(), random);
        if (moverId.equals(anchorId) && effect.targetSelf()) {
            // A card aimed at its own caster names nobody to follow: the caster's next turn
            // then comes right after whoever acts next.
            anchorId = nextToActBesides(timeline, combatants, moverId);
        }
        if (anchorId == null || moverId.equals(anchorId)) {
            return unchanged;
        }
        CombatantState mover = find(combatants, moverId);
        if (mover == null) {
            mover = moverId.equals(target.combatantId()) ? target : null;
        }
        if (mover == null || mover.hp() <= 0) {
            return unchanged;
        }
        if (relicEngine != null && !relicEngine.canTurnBeMoved(mover, true, true)) {
            return unchanged;
        }
        CombatantState anchor = find(combatants, anchorId);
        if (anchor != null && anchor.hp() <= 0) {
            // Nobody takes a turn after a corpse's: its entry stays where it fell.
            return unchanged;
        }

        TimelineEntry moverEntry = entryOf(timeline, moverId);
        TimelineEntry anchorEntry = entryOf(timeline, anchorId);
        if (moverEntry == null || anchorEntry == null) {
            return unchanged;
        }

        String activeId = activeCombatantId(timeline, combatants);
        List<TimelineEntry> moved = new ArrayList<>();
        for (TimelineEntry entry : timeline) {
            if (!entry.combatantId().equals(moverId)) {
                moved.add(entry);
                continue;
            }
            moved.add(moverId.equals(activeId)
                    ? afterTheTurnUnderWay(entry, anchorEntry, mover, card, cardModifiers,
                            timeline, combatants)
                    : rightBehind(entry, anchorEntry, timeline));
        }
        return new ResolutionStep(target, moved, List.of(), random);
    }

    /**
     * The mover's own turn is the one under way, and rewriting its tick achieves nothing:
     * {@code endTurn} reschedules that entry from the tick it started at plus the wait it owes.
     * What survives the reschedule is the pending delay, the channel AdvanceTurn and DelayTurn use
     * too, so the amount is whatever makes the rescheduled turn land one tick after the anchor's.
     */
    private static TimelineEntry afterTheTurnUnderWay(
            TimelineEntry entry,
            TimelineEntry anchorEntry,
            CombatantState mover,
            CardDefinition card,
            List<fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier> cardModifiers,
            List<TimelineEntry> timeline,
            List<CombatantState> combatants
    ) {
        long wait = Math.max(0, BattleCalculator.getModifiedValue(
                mover.turnDelayTicks(),
                StatType.TURN_DELAY,
                new EffectContext(null, mover, null, List.of(), card, false, cardModifiers,
                        timeline, combatants)));
        long wouldLandAt = Math.addExact(
                Math.addExact(entry.readyAtTick(), wait), entry.pendingSelfDelayTicks());
        long desired = Math.addExact(anchorEntry.readyAtTick(), 1);
        return entry.withPendingSelfDelay(desired - wouldLandAt);
    }

    /** Whoever holds the earliest living entry once {@code excludedId} is set aside. */
    private static String nextToActBesides(
            List<TimelineEntry> timeline, List<CombatantState> combatants, String excludedId) {
        Set<String> dead = new HashSet<>();
        for (CombatantState combatant : combatants) {
            if (combatant.hp() <= 0) {
                dead.add(combatant.combatantId());
            }
        }
        return timeline.stream()
                .filter(entry -> !entry.combatantId().equals(excludedId))
                .filter(entry -> !dead.contains(entry.combatantId()))
                .sorted(TimelineEntry.CANONICAL_ORDER)
                .map(TimelineEntry::combatantId)
                .findFirst()
                .orElse(null);
    }

    /**
     * An entry that is not under way can simply be moved: onto the anchor's tick, behind the
     * anchor and behind whatever already queues at that tick behind it -- another target of the
     * same card cut in a moment ago keeps its place ahead of this one.
     *
     * <p>When the anchor is the turn under way, that tick is the current one: the mover then
     * comes up the moment the turn ends, before anybody else's next turn.
     */
    private static TimelineEntry rightBehind(
            TimelineEntry entry, TimelineEntry anchorEntry, List<TimelineEntry> timeline) {
        long lastInLine = anchorEntry.sequence();
        for (TimelineEntry other : timeline) {
            if (other.combatantId().equals(entry.combatantId())) {
                continue;
            }
            if (other.readyAtTick() == anchorEntry.readyAtTick()
                    && other.sequence() >= lastInLine) {
                lastInLine = other.sequence();
            }
        }
        return new TimelineEntry(entry.combatantId(), anchorEntry.readyAtTick(),
                Math.incrementExact(lastInLine));
    }

    /**
     * Whose turn is under way: the earliest entry of somebody still standing.
     *
     * <p>A combatant the list does not name is taken to be standing, so a caller handing over
     * only part of the table does not make somebody else look like the one acting.
     */
    private static String activeCombatantId(
            List<TimelineEntry> timeline, List<CombatantState> combatants) {
        Set<String> dead = new HashSet<>();
        for (CombatantState combatant : combatants) {
            if (combatant.hp() <= 0) {
                dead.add(combatant.combatantId());
            }
        }
        return timeline.stream()
                .filter(entry -> !dead.contains(entry.combatantId()))
                .sorted(TimelineEntry.CANONICAL_ORDER)
                .map(TimelineEntry::combatantId)
                .findFirst()
                .orElse(null);
    }

    private static TimelineEntry entryOf(List<TimelineEntry> timeline, String combatantId) {
        return timeline.stream()
                .filter(entry -> entry.combatantId().equals(combatantId))
                .findFirst()
                .orElse(null);
    }

    private static CombatantState find(List<CombatantState> combatants, String combatantId) {
        return combatants.stream()
                .filter(combatant -> combatant.combatantId().equals(combatantId))
                .findFirst()
                .orElse(null);
    }
}
