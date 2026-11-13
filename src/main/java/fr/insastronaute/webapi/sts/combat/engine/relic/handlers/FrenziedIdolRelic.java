package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class FrenziedIdolRelic implements RelicHandler {

    @Override
    public String id() {
        return "FrenziedIdolRelic";
    }

    @Override
    public CombatantState onCombatStart(CombatState state, CombatantState owner, List<CombatEvent> events) {
        List<StatusInstance> statuses = new ArrayList<>(owner.statuses());
        statuses.add(new StatusInstance("Burn", 0, 6, "", 0));
        return withStatuses(owner, statuses);
    }

    @Override
    public ResolutionStep onDamageTaken(
            CombatantState owner,
            int amount,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            UUID combatId,
            long revision,
            List<CombatEvent> events
    ) {
        if (amount <= 0) {
            return new ResolutionStep(combatants, timeline, events, random);
        }

        List<StatusInstance> statuses = new ArrayList<>(owner.statuses());
        statuses.add(new StatusInstance("Strength", 1, -1, "", 0));
        CombatantState updated = withStatuses(owner, statuses);
        List<CombatantState> result = new ArrayList<>(combatants);
        for (int index = 0; index < result.size(); index++) {
            if (result.get(index).combatantId().equals(updated.combatantId())) {
                result.set(index, updated);
                break;
            }
        }
        return new ResolutionStep(result, timeline, events, random);
    }

    private static CombatantState withStatuses(CombatantState owner, List<StatusInstance> statuses) {
        return new CombatantState(owner.combatantId(), owner.teamId(), owner.controllerType(),
                owner.maxHp(), owner.hp(), owner.armor(), owner.energy(), owner.baseEnergy(),
                owner.turnDelayTicks(), owner.piles(), statuses, owner.relics(), owner.intentCardId(),
                owner.patternIndex(), owner.relicMemory());
    }
}
