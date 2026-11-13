package fr.insastronaute.webapi.sts.combat.engine.effect;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ExtendStatusesHandler implements EffectHandler {

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
        // Extending a status adds turns, not value: Unity increases the duration and skips both
        // the statuses marked inextendable and the permanent ones, which have no clock to grow.
        List<StatusInstance> newStatuses = new ArrayList<>();

        for (StatusInstance status : target.statuses()) {
            boolean inextendable = fr.insastronaute.webapi.sts.combat.engine.status.StatusCatalog
                    .of(status.statusType()).inextendable();
            if (inextendable || status.duration() <= 0) {
                newStatuses.add(status);
                continue;
            }
            newStatuses.add(new StatusInstance(
                    status.statusType(),
                    status.value(),
                    status.duration() + effect.value(),
                    status.cardId(),
                    status.index()
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
                target.piles(),
                newStatuses,
                target.relics(),
                target.intentCardId(),
                target.patternIndex(), target.relicMemory());

        return new ResolutionStep(updated, timeline, List.of(), random);
    }
}
