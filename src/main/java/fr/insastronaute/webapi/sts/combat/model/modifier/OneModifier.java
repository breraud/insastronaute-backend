package fr.insastronaute.webapi.sts.combat.model.modifier;

import fr.insastronaute.webapi.sts.combat.engine.EffectContext;

public record OneModifier(StatType type) implements StatModifier {
    @Override
    public ModifierType modifierType() {
        return ModifierType.OVERRIDE;
    }

    @Override
    public int modify(int value, EffectContext ctx) {
        return 1;
    }
}
