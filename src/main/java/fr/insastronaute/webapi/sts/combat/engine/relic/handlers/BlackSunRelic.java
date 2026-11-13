package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors {@code Boss/BlackSunRelic.cs}: at combat start, +3 Force, +3 Dexterity, 1 turn of
 * Weakness and 1 turn of Vulnerable.
 */
@Component
public class BlackSunRelic implements RelicHandler {

    @Override
    public String id() {
        return "BlackSunRelic";
    }

    @Override
    public CombatantState onCombatStart(CombatState state, CombatantState owner, List<CombatEvent> events) {
        List<StatusInstance> statuses = new ArrayList<>(owner.statuses());
        statuses.add(new StatusInstance("Strength", 3, -1, "", 0));
        statuses.add(new StatusInstance("Dexterity", 3, -1, "", 0));
        statuses.add(new StatusInstance("Weakness", 0, 1, "", 0));
        statuses.add(new StatusInstance("Vuln", 0, 1, "", 0));
        return new CombatantState(
                owner.combatantId(), owner.teamId(), owner.controllerType(),
                owner.maxHp(), owner.hp(), owner.armor(), owner.energy(),
                owner.baseEnergy(), owner.turnDelayTicks(), owner.piles(),
                statuses, owner.relics(), owner.intentCardId(), owner.patternIndex(),
                owner.relicMemory());
    }
}
