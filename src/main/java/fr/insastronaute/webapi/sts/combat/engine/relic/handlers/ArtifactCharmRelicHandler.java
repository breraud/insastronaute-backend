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
public class ArtifactCharmRelicHandler implements RelicHandler {
    @Override
    public String id() { return "ArtifactCharmRelic"; }

    @Override
    public CombatantState onCombatStart(CombatState state, CombatantState owner, List<CombatEvent> events) {
        List<StatusInstance> statuses = new ArrayList<>(owner.statuses());
        statuses.add(new StatusInstance("Artifact", 1, -1, "", 0));
        return new CombatantState(
                owner.combatantId(), owner.teamId(), owner.controllerType(),
                owner.maxHp(), owner.hp(), owner.armor(), owner.energy(),
                owner.baseEnergy(), owner.turnDelayTicks(), owner.piles(),
                statuses, owner.relics(), owner.intentCardId(), owner.patternIndex(), owner.relicMemory());
    }
}
