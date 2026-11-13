package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AdvanceTurnHandler implements EffectHandler {

    private final fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine relicEngine;

    public AdvanceTurnHandler(fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine relicEngine) {
        this.relicEngine = relicEngine;
    }

    public AdvanceTurnHandler() {
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
        // The authored number is only the starting point: a card can scale it (Prolongement
        // delays by eight per tick the target still has to wait), an enchantment can stretch it,
        // and the immunity statuses override it to zero. None of that reached here, because the
        // value went straight from the effect into the arithmetic without ever being asked of the
        // calculator -- so a card whose whole delay comes from a modifier delayed by nothing at
        // all, and being immune to a delay did not prevent one.
        int advancePercent = fr.insastronaute.webapi.sts.combat.engine.BattleCalculator.getModifiedValue(
                effect.value(),
                fr.insastronaute.webapi.sts.combat.model.modifier.StatType.TURN_MANIPULATION_ADVANCE,
                new fr.insastronaute.webapi.sts.combat.engine.EffectContext(
                        null,
                        combatants.stream()
                                .filter(combatant -> combatant.combatantId().equals(sourceId))
                                .findFirst()
                                .orElse(null),
                        target, java.util.List.of(target), card, false, cardModifiers,
                        timeline, combatants));
        float turnDelay = Math.max(1f, target.turnDelayTicks());
        long advanceAmount = (long) (turnDelay * advancePercent / 100f);

        // A turn only moves if whoever owns it allows it: an anchoring relic refuses outright,
        // and an immunity status zeroes the amount.
        if (relicEngine != null && !relicEngine.canTurnBeMoved(target, true, false)) {
            return new ResolutionStep(target, timeline, List.of(), random);
        }

        java.util.Set<String> living = combatants.stream()
                .filter(combatant -> combatant.hp() > 0)
                .map(fr.insastronaute.webapi.sts.combat.model.CombatantState::combatantId)
                .collect(java.util.stream.Collectors.toSet());
        boolean isTargetsOwnTurn = fr.insastronaute.webapi.sts.combat.model.TimelineEntry.isActiveNow(
                target.combatantId(), timeline, living);

        List<TimelineEntry> newTimeline = new ArrayList<>();
        for (TimelineEntry entry : timeline) {
            if (!entry.combatantId().equals(target.combatantId())) {
                newTimeline.add(entry);
                continue;
            }
            // The turn under way is never itself moved -- only the one that follows it, once
            // it actually ends, is: doing otherwise reshuffles the sorted timeline mid-turn and
            // can make holdsTheTurn see somebody else as active before this turn is even over.
            newTimeline.add(isTargetsOwnTurn
                    ? entry.withPendingSelfDelay(-advanceAmount)
                    : new TimelineEntry(entry.combatantId(), Math.max(0, entry.readyAtTick() - advanceAmount), entry.sequence()));
        }

        return new ResolutionStep(target, newTimeline, List.of(), random);
    }
}
