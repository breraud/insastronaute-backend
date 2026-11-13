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

public final class SetStatusToMaxValueHandler implements EffectHandler {

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
        List<StatusInstance> newStatuses = new ArrayList<>();

        for (StatusInstance status : target.statuses()) {
            if (status.statusType().equals(effect.statusType())) {
                // In Unity, each status can define its own maxValue (default 99).
                int maxValue = 99;
                if ("Crystallize".equals(status.statusType())) {
                    maxValue = 3;
                }
                newStatuses.add(new StatusInstance(
                        status.statusType(),
                        maxValue,
                        status.duration(),
                        status.cardId(),
                        status.index()
                ));
            } else {
                newStatuses.add(status);
            }
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
                target.piles(),
                newStatuses,
                target.relics(),
                target.intentCardId(),
                target.patternIndex(), target.relicMemory());

        return new ResolutionStep(updated, timeline, List.of(), random);
    }
}
