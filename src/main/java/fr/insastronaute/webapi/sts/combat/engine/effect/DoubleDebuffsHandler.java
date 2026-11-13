package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.StatusApplied;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusCatalog;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DoubleDebuffsHandler implements EffectHandler {


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
        // Unity doubles a debuff by applying it a second time, so the stacking rules decide what
        // "double" means: a timed debuff gains its remaining turns, a permanent one gains its
        // value. Doubling the value of Vulnerable, whose magnitude lives in its duration and
        // whose value is 0, doubled nothing at all.
        List<StatusInstance> sourceStatuses = combatants.stream()
                .filter(combatant -> combatant.combatantId().equals(sourceId))
                .findFirst()
                .map(CombatantState::statuses)
                .orElse(List.of());
        List<StatusInstance> alreadyCarried = List.copyOf(target.statuses());
        List<StatusInstance> newStatuses = new ArrayList<>(alreadyCarried);
        // Every doubling is announced. The maths was always right, but the handler returned an
        // empty event list, so nothing reached the client: no number moved on screen, no popup,
        // and the card read as doing nothing at all until the next full state sync happened to
        // land.
        List<CombatEvent> events = new ArrayList<>();
        for (StatusInstance status : alreadyCarried) {
            if (!StatusCatalog.isDebuff(status)) {
                continue;
            }
            fr.insastronaute.webapi.sts.combat.engine.status.StatusOperations.Application applied =
                    fr.insastronaute.webapi.sts.combat.engine.status.StatusOperations
                            .apply(newStatuses, status, sourceStatuses);
            newStatuses = new ArrayList<>(applied.statuses());
            if (applied.applied() && applied.result() != null) {
                events.add(new StatusApplied(
                        combatId,
                        revision,
                        sourceId,
                        target.combatantId(),
                        applied.result().statusType(),
                        applied.result().value(),
                        applied.result().duration(),
                        true));
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

        return new ResolutionStep(updated, timeline, events, random);
    }
}
