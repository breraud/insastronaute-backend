package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusOperations;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Cathedral knell: every blow its owner lands gives them one Strength, and they open each combat
 * under four turns of Weakness ({@code DreadBellRelic.cs}).
 *
 * <p>That opening Weakness cannot be stripped, as Unity marks it on the instance: it is what the
 * relic costs, and dispelling it would hand the player the Strength without the price.
 */
@Component
public class DreadBellRelic implements RelicHandler {

    @Override
    public String id() {
        return "DreadBellRelic";
    }

    @Override
    public CombatantState onCombatStart(
            CombatState state, CombatantState owner, List<CombatEvent> events) {
        return withStatuses(owner, StatusOperations.apply(
                owner.statuses(),
                StatusInstance.unremovable("Weakness", 0, 4),
                owner.statuses()).statuses());
    }

    @Override
    public CombatantState onDamageDealt(CombatantState owner, CombatantState target, int amount) {
        if (amount <= 0 || target == null || target.teamId().equals(owner.teamId())) {
            return owner;
        }
        return withStatuses(owner, StatusOperations.apply(
                owner.statuses(),
                new StatusInstance("Strength", 1, -1, "", owner.statuses().size()),
                owner.statuses()).statuses());
    }

    private static CombatantState withStatuses(
            CombatantState owner, List<StatusInstance> statuses) {
        return new CombatantState(
                owner.combatantId(),
                owner.teamId(),
                owner.controllerType(),
                owner.maxHp(),
                owner.hp(),
                owner.armor(),
                owner.energy(),
                owner.baseEnergy(),
                owner.turnDelayTicks(),
                owner.piles(),
                statuses,
                owner.relics(),
                owner.intentCardId(),
                owner.patternIndex(),
                owner.relicMemory());
    }
}
