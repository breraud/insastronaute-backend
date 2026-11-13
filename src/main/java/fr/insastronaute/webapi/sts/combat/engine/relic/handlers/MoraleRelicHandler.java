package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class MoraleRelicHandler implements RelicHandler {
    @Override
    public String id() { return "MoraleRelic"; }

    @Override
    public CombatantState onTurnStart(CombatState state, CombatantState owner, List<CombatEvent> events) {
        if (owner.hp() < owner.maxHp() / 2) {
            int newHp = Math.min(owner.maxHp(), owner.hp() + 1);
            return new CombatantState(
                owner.combatantId(), owner.teamId(), owner.controllerType(),
                owner.maxHp(), newHp, owner.armor(), owner.energy(),
                owner.baseEnergy(), owner.turnDelayTicks(), owner.piles(),
                owner.statuses(), owner.relics(), owner.intentCardId(), owner.patternIndex(), owner.relicMemory());
        }
        return owner;
    }
}
