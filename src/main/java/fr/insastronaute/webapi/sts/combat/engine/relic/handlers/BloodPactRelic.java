package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BloodPactRelic implements RelicHandler {

    @Override
    public String id() {
        return "BloodPactRelic";
    }

    @Override
    public HealInterceptionResult onHeal(
            CombatState state,
            CombatantState owner,
            int amount,
            List<CombatEvent> events
    ) {
        if (amount <= 0) {
            return new HealInterceptionResult(amount, owner);
        }

        return new HealInterceptionResult(amount, new CombatantState(
                owner.combatantId(), owner.teamId(), owner.controllerType(),
                owner.maxHp(), owner.hp(), owner.armor(), owner.energy() + 1,
                owner.baseEnergy(), owner.turnDelayTicks(), owner.piles(), owner.statuses(),
                owner.relics(), owner.intentCardId(), owner.patternIndex(), owner.relicMemory()));
    }
}
