package fr.insastronaute.webapi.sts.combat.model.modifier;

import fr.insastronaute.webapi.sts.combat.engine.EffectContext;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusCatalog;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.List;
import java.util.function.Predicate;

/**
 * Adds a flat amount for each status of a given side on a given combatant.
 *
 * <p>The debuff side counts everything that is not a buff, matching how the game selects debuffs
 * elsewhere, so a neutral status counts towards it too.
 */
public record StatusCountModifier(StatType type, int perStatus, Side side, boolean onTarget)
        implements StatModifier {

    public enum Side {
        BUFF(StatusCatalog::isBuff),
        NOT_BUFF(status -> !StatusCatalog.isBuff(status));

        private final Predicate<StatusInstance> matches;

        Side(Predicate<StatusInstance> matches) {
            this.matches = matches;
        }

        boolean matches(StatusInstance status) {
            return matches.test(status);
        }
    }

    @Override
    public ModifierType modifierType() {
        return ModifierType.ADDITIVE;
    }

    @Override
    public int modify(int value, EffectContext ctx) {
        CombatantState holder = ctx == null ? null : onTarget ? ctx.target() : ctx.source();
        if (holder == null) {
            return value;
        }
        List<StatusInstance> statuses = holder.statuses();
        long count = statuses.stream().filter(side::matches).count();
        return value + (int) count * perStatus;
    }
}
