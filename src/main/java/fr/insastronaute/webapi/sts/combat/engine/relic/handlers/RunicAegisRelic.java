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
 * Mirrors {@code Boss/RunicAegisRelic.cs}: at combat start, +15 Armour, +1 Artefact, and −1
 * energy.
 *
 * <p>Unity grants the armour on {@code OnTurnStart} and blocks 1 energy through {@code
 * EnergyOnTurnStart}, both guarded to fire only once, purely so they survive its own
 * start-of-turn armour/energy reset. The Java engine runs no such reset before the very first
 * turn, so applying them directly here reproduces the same one-time effect for the combatant who
 * acts first.
 */
@Component
public class RunicAegisRelic implements RelicHandler {

    @Override
    public String id() {
        return "RunicAegisRelic";
    }

    @Override
    public CombatantState onCombatStart(CombatState state, CombatantState owner, List<CombatEvent> events) {
        List<StatusInstance> statuses = new ArrayList<>(owner.statuses());
        statuses.add(new StatusInstance("Artifact", 1, -1, "", 0));
        int newArmor = owner.armor() + 15;
        int newEnergy = Math.max(0, owner.energy() - 1);
        return new CombatantState(
                owner.combatantId(), owner.teamId(), owner.controllerType(),
                owner.maxHp(), owner.hp(), newArmor, newEnergy,
                owner.baseEnergy(), owner.turnDelayTicks(), owner.piles(),
                statuses, owner.relics(), owner.intentCardId(), owner.patternIndex(),
                owner.relicMemory());
    }
}
