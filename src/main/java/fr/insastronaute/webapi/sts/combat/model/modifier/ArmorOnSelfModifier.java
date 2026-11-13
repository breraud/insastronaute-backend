package fr.insastronaute.webapi.sts.combat.model.modifier;

import fr.insastronaute.webapi.sts.combat.engine.EffectContext;

/** Adds a flat amount for each point of armour the caster is standing behind. */
public record ArmorOnSelfModifier(StatType type, int perArmor) implements StatModifier {

    @Override
    public ModifierType modifierType() {
        return ModifierType.ADDITIVE;
    }

    @Override
    public int modify(int value, EffectContext ctx) {
        if (ctx == null || ctx.source() == null) {
            return value;
        }
        return value + perArmor * ctx.source().armor();
    }
}
