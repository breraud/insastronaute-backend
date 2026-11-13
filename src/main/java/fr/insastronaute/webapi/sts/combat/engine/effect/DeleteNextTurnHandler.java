package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DeleteNextTurnHandler implements EffectHandler {

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
        List<TimelineEntry> newTimeline = new ArrayList<>();
        TimelineEntry targetNextTurn = null;
        
        for (TimelineEntry entry : timeline) {
            if (entry.combatantId().equals(target.combatantId())) {
                if (targetNextTurn == null || entry.readyAtTick() < targetNextTurn.readyAtTick()) {
                    targetNextTurn = entry;
                }
            }
        }
        
        for (TimelineEntry entry : timeline) {
            if (!entry.equals(targetNextTurn)) {
                newTimeline.add(entry);
            }
        }

        // Apply a delay equivalent to one full turn to push them back
        float turnDelay = Math.max(1f, target.turnDelayTicks());
        if (targetNextTurn != null) {
             newTimeline.add(new TimelineEntry(
                     target.combatantId(),
                     targetNextTurn.readyAtTick() + (long) turnDelay,
                     targetNextTurn.sequence() + 1
             ));
        }

        return new ResolutionStep(target, newTimeline, List.of(), random);
    }
}
