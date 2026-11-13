package fr.insastronaute.webapi.sts.combat.model.modifier;

import fr.insastronaute.webapi.sts.combat.engine.EffectContext;

/** Scales with how wounded the caster is, by a percentage per missing hit point. */
public record LostHpModifier(StatType type, int percentPerMissingHp) implements StatModifier {

    @Override
    public ModifierType modifierType() {
        return ModifierType.MULTIPLICATIVE;
    }

    @Override
    public int modify(int value, EffectContext ctx) {
        if (ctx == null || ctx.source() == null) {
            return value;
        }
        int missing = ctx.source().maxHp() - ctx.source().hp();
        return Math.round((float) (value * (1.0 + percentPerMissingHp * missing / 100.0)));
    }
}
