package fr.insastronaute.webapi.sts.combat.model.modifier;

import fr.insastronaute.webapi.sts.combat.engine.EffectContext;

public record PercentModifier(StatType type, int percentage) implements StatModifier {

    @Override
    public ModifierType modifierType() {
        return ModifierType.MULTIPLICATIVE;
    }

    @Override
    public int modify(int value, EffectContext ctx) {
        return value + (value * percentage / 100);
    }
}
