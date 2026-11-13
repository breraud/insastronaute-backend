package fr.insastronaute.webapi.sts.combat.model.modifier;

import fr.insastronaute.webapi.sts.combat.engine.EffectContext;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;

/**
 * Scales with how much of one named status a combatant carries.
 *
 * <p>A status counts for at least one as soon as it is there, so a status that only has a duration
 * still contributes.
 */
public record SpecificStatusModifier(
        StatType type,
        int perStatus,
        String statusType,
        boolean onTarget
) implements StatModifier {

    @Override
    public ModifierType modifierType() {
        return ModifierType.ADDITIVE;
    }

    @Override
    public int modify(int value, EffectContext ctx) {
        CombatantState holder = ctx == null ? null : onTarget ? ctx.target() : ctx.source();
        if (holder == null || statusType == null) {
            return value;
        }
        return holder.statuses().stream()
                .filter(status -> statusType.equals(status.statusType()))
                .findFirst()
                .map(this::contribution)
                .map(contribution -> value + contribution)
                .orElse(value);
    }

    private int contribution(StatusInstance status) {
        int magnitude = Math.max(0, status.value()) + Math.max(0, status.duration());
        return perStatus * Math.max(1, magnitude);
    }
}
