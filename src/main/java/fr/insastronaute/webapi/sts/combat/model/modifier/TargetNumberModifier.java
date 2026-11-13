package fr.insastronaute.webapi.sts.combat.model.modifier;

import fr.insastronaute.webapi.sts.combat.engine.EffectContext;

public record TargetNumberModifier(StatType type, int percentPerTarget) implements StatModifier {
    @Override
    public ModifierType modifierType() {
        return ModifierType.MULTIPLICATIVE;
    }

    @Override
    public int modify(int value, EffectContext ctx) {
        int targetCount = (ctx != null && ctx.targets() != null) ? ctx.targets().size() : 0;
        return value + (value * (percentPerTarget * targetCount) / 100);
    }
}
