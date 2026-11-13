package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JuryRelic implements RelicHandler {
    @Override
    public String id() { return "JuryRelic"; }

    @Override
    public CombatantState onTurnEnd(CombatState state, CombatantState owner, List<CombatEvent> events) {
        return new CombatantState(owner.combatantId(), owner.teamId(), owner.controllerType(),
                owner.maxHp(), owner.hp(), owner.armor() + 5, owner.energy(), owner.baseEnergy(),
                owner.turnDelayTicks(), owner.piles(), owner.statuses(), owner.relics(),
                owner.intentCardId(), owner.patternIndex(), owner.relicMemory());
    }
}
