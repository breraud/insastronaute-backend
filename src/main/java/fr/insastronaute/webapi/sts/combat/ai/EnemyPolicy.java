package fr.insastronaute.webapi.sts.combat.ai;

import fr.insastronaute.webapi.sts.combat.command.CombatCommand;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;

@FunctionalInterface
public interface EnemyPolicy {

    CombatCommand nextCommand(CombatState state);

    default CombatCommand predictCommand(CombatState state, CombatantState actor) {
        throw new UnsupportedOperationException("predictCommand not implemented for " + getClass().getSimpleName());
    }
}
