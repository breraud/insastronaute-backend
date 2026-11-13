package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PocketBatteryRelicHandler implements RelicHandler {
    
    private static final String USED_STATUS = "PocketBatteryUsed";

    @Override
    public String id() {
        return "PocketBatteryRelic";
    }

    @Override
    public CombatantState onTurnStart(CombatState state, CombatantState owner, List<CombatEvent> events) {
        if (owner.statuses().stream().anyMatch(s -> s.statusType().equals(USED_STATUS))) {
            return owner;
        }

        List<StatusInstance> newStatuses = new ArrayList<>(owner.statuses());
        newStatuses.add(new StatusInstance(USED_STATUS, 1, -1, "", 0));
        
        return new CombatantState(
                owner.combatantId(), owner.teamId(), owner.controllerType(),
                owner.maxHp(), owner.hp(), owner.armor(),
                owner.energy() + 1, owner.baseEnergy(), owner.turnDelayTicks(),
                owner.piles(), newStatuses, owner.relics(), owner.intentCardId(), owner.patternIndex(), owner.relicMemory());
    }
}
