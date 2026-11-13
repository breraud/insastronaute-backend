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
 * Mirrors {@code Boss/TitanHeartRelic.cs}: at combat start, +20 Armour, +1 Force, +1 Dexterity,
 * and Fragile for the whole combat.
 *
 * <p>Unity grants the armour on {@code OnTurnStart} guarded by a one-shot flag, purely so it
 * survives its own start-of-turn armour reset; the Java engine runs no such reset before the very
 * first turn, so granting it directly here reproduces the same one-time effect for the combatant
 * who acts first.
 *
 * <p>The Fragile it charges you with lasts the whole combat and cannot be stripped: it is the
 * price of the relic, so a card that dispels debuffs must not cancel it (TitanHeartRelic.cs:19-21
 * marks that instance, not every Fragile).
 */
@Component
public class TitanHeartRelic implements RelicHandler {

    @Override
    public String id() {
        return "TitanHeartRelic";
    }

    @Override
    public CombatantState onCombatStart(CombatState state, CombatantState owner, List<CombatEvent> events) {
        List<StatusInstance> statuses = new ArrayList<>(owner.statuses());
        statuses.add(new StatusInstance("Strength", 1, -1, "", 0));
        statuses.add(new StatusInstance("Dexterity", 1, -1, "", 0));
        statuses.add(StatusInstance.unremovable("Fragile", 0, 99));
        int newArmor = owner.armor() + 20;
        return new CombatantState(
                owner.combatantId(), owner.teamId(), owner.controllerType(),
                owner.maxHp(), owner.hp(), newArmor, owner.energy(),
                owner.baseEnergy(), owner.turnDelayTicks(), owner.piles(),
                statuses, owner.relics(), owner.intentCardId(), owner.patternIndex(),
                owner.relicMemory());
    }
}
