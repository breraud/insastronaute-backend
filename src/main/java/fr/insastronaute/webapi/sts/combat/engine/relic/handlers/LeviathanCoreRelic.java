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
 * Mirrors {@code Boss/LeviathanCoreRelic.cs}: at combat start, +2 energy and +1 Force.
 *
 * <p>Unity gates the energy grant on {@code OnTurnStart} purely so it survives its own
 * start-of-turn energy reset; the Java engine runs no such reset before the very first turn, so
 * granting it directly here reproduces the same one-time effect for the combatant who acts first.
 */
@Component
public class LeviathanCoreRelic implements RelicHandler {

    @Override
    public String id() {
        return "LeviathanCoreRelic";
    }

    @Override
    public CombatantState onCombatStart(CombatState state, CombatantState owner, List<CombatEvent> events) {
        List<StatusInstance> statuses = new ArrayList<>(owner.statuses());
        statuses.add(new StatusInstance("Strength", 1, -1, "", 0));
        int newEnergy = owner.energy() + 2;
        return new CombatantState(
                owner.combatantId(), owner.teamId(), owner.controllerType(),
                owner.maxHp(), owner.hp(), owner.armor(), newEnergy,
                owner.baseEnergy(), owner.turnDelayTicks(), owner.piles(),
                statuses, owner.relics(), owner.intentCardId(), owner.patternIndex(),
                owner.relicMemory());
    }
}
