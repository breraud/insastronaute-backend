package fr.insastronaute.webapi.sts.combat.model.modifier;

import fr.insastronaute.webapi.sts.combat.engine.EffectContext;

public record ArmorModifier(StatType type, int percentPerArmor) implements StatModifier {
    @Override
    public ModifierType modifierType() {
        return ModifierType.MULTIPLICATIVE;
    }

    @Override
    public int modify(int value, EffectContext ctx) {
        int armor = (ctx != null && ctx.target() != null) ? ctx.target().armor() : 0;
        return value + (value * (percentPerArmor * armor) / 100);
    }
}
