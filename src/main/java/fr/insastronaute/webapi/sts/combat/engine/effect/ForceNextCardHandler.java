package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ForceNextCardHandler implements EffectHandler {

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
        if (effect.cardId() == null || effect.cardId().isBlank()) {
            return new ResolutionStep(target, timeline, List.of(), random);
        }

        List<StatusInstance> newStatuses = new ArrayList<>(target.statuses());
        // One charge per turn forced. It runs out by being played, never by the turn clock, so it
        // is carried without a duration: a zero one expires on the very next tick, before the
        // enemy it was forced on ever got its turn.
        newStatuses.add(new StatusInstance(
                "ForcedAction",
                Math.max(1, effect.value()),
                -1,
                effect.cardId(),
                0
        ));

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
                target.piles(),
                newStatuses,
                target.relics(),
                target.intentCardId(),
                target.patternIndex(), target.relicMemory());

        return new ResolutionStep(updated, timeline, List.of(), random);
    }
}
