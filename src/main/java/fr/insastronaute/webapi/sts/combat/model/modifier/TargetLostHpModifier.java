package fr.insastronaute.webapi.sts.combat.model.modifier;

import fr.insastronaute.webapi.sts.combat.engine.EffectContext;

/** Scales with how wounded the target is, by a percentage per missing hit point. */
public record TargetLostHpModifier(StatType type, int percentPerMissingHp) implements StatModifier {

    @Override
    public ModifierType modifierType() {
        return ModifierType.MULTIPLICATIVE;
    }

    @Override
    public int modify(int value, EffectContext ctx) {
        if (ctx == null || ctx.target() == null) {
            return value;
        }
        int missing = ctx.target().maxHp() - ctx.target().hp();
        return value * (1 + percentPerMissingHp * missing / 100);
    }
}
