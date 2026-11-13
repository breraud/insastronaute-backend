package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.event.EnergyGained;
import fr.insastronaute.webapi.sts.combat.event.StatusApplied;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

public final class GainEnergyHandler implements EffectHandler {

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
        int requested = effect.value();
        long capacity = (long) Integer.MAX_VALUE - target.energy();
        int applied = (int) Math.min((long) requested, capacity);
        int resultingEnergy = target.energy() + applied;
        List<fr.insastronaute.webapi.sts.combat.model.StatusInstance> statuses =
                new ArrayList<>(target.statuses());
        List<fr.insastronaute.webapi.sts.combat.model.StatusInstance> vigorGained = new ArrayList<>();
        for (int index = 0; index < statuses.size(); index++) {
            var status = statuses.get(index);
            if (!"Charge".equals(status.statusType())) {
                continue;
            }
            int next = status.value() + applied;
            if (next >= 3) {
                next -= 3;
                vigorGained.add(new fr.insastronaute.webapi.sts.combat.model.StatusInstance(
                        "Vigor", 2, -1, status.cardId(), 0));
            }
            statuses.set(index, new fr.insastronaute.webapi.sts.combat.model.StatusInstance(
                    status.statusType(), next, status.duration(), status.cardId(), status.index(),
                    status.removable(), status.resistsEverything(), status.progress()));
        }
        // La Vigueur s'ajoute à celle déjà portée, et l'événement dit ce qu'elle vaut désormais :
        // posée à part, elle faisait deux lignes de Vigueur, et annoncée à 2 elle montrait au
        // client 2 là où le porteur en avait davantage.
        List<fr.insastronaute.webapi.sts.combat.model.StatusInstance> vigorTotals = new ArrayList<>();
        for (var vigor : vigorGained) {
            vigorTotals.add(fr.insastronaute.webapi.sts.combat.engine.status.StatusOperations
                    .insert(statuses, vigor));
        }
        CombatantState updated = new CombatantState(
                target.combatantId(),
                target.teamId(),
                target.controllerType(),
                target.maxHp(),
                target.hp(),
                target.armor(),
                resultingEnergy,
                target.baseEnergy(),
                target.turnDelayTicks(),
                target.piles(),
                statuses,
                target.relics(),
                target.intentCardId(),
                target.patternIndex(), target.relicMemory());
        EnergyGained event = new EnergyGained(
                combatId,
                revision,
                target.combatantId(),
                applied,
                resultingEnergy
        );
        List<fr.insastronaute.webapi.sts.combat.event.CombatEvent> events = new ArrayList<>();
        events.add(event);
        for (var vigor : vigorTotals) {
            events.add(new StatusApplied(combatId, revision, target.combatantId(),
                    target.combatantId(), vigor.statusType(), vigor.value(), vigor.duration(),
                    false));
        }
        return new ResolutionStep(updated, timeline, events, random);
    }
}
