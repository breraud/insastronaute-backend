package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ReinforcedBuckleRelicHandler implements RelicHandler {
    
    private static final String USED_STATUS = "ReinforcedBuckleUsed";

    @Override
    public String id() {
        return "ReinforcedBuckleRelic";
    }

    /** "La première fois par combat que <em>vous</em> gagnez de l'Armure" : l'armure d'un autre ne compte pas. */
    @Override
    public List<CombatantState> onArmorGain(
            CombatantState owner,
            CombatantState target,
            int amount,
            List<CombatantState> combatants,
            List<CombatEvent> events
    ) {
        if (!owner.combatantId().equals(target.combatantId())) {
            return combatants;
        }
        if (amount <= 0 || target.statuses().stream().anyMatch(s -> s.statusType().equals(USED_STATUS))) {
            return combatants;
        }

        List<StatusInstance> newStatuses = new ArrayList<>(target.statuses());
        newStatuses.add(new StatusInstance(USED_STATUS, 1, -1, "", 0));
        
        CombatantState updatedTarget = new CombatantState(
                target.combatantId(), target.teamId(), target.controllerType(),
                target.maxHp(), target.hp(), target.armor() + 5,
                target.energy(), target.baseEnergy(), target.turnDelayTicks(),
                target.piles(), newStatuses, target.relics(), target.intentCardId(), target.patternIndex(), target.relicMemory());
        
        List<CombatantState> newCombatants = new ArrayList<>(combatants);
        for (int i = 0; i < newCombatants.size(); i++) {
            if (newCombatants.get(i).combatantId().equals(updatedTarget.combatantId())) {
                newCombatants.set(i, updatedTarget);
                break;
            }
        }
        return newCombatants;
    }
}
