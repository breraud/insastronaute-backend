package fr.insastronaute.webapi.sts.combat.model.modifier;

import fr.insastronaute.webapi.sts.combat.engine.EffectContext;

public record FlatModifier(StatType type, int amount) implements StatModifier {
    @Override
    public ModifierType modifierType() {
        return ModifierType.ADDITIVE;
    }

    @Override
    public int modify(int value, EffectContext ctx) {
        return value + amount;
    }
}
