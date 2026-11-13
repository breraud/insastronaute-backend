package fr.insastronaute.webapi.sts.combat.model.modifier;

import fr.insastronaute.webapi.sts.combat.engine.EffectContext;

/** Adds a flat amount for each point of armour across everything the card is aimed at. */
public record ArmorOnTargetModifier(StatType type, int perArmor) implements StatModifier {

    @Override
    public ModifierType modifierType() {
        return ModifierType.ADDITIVE;
    }

    @Override
    public int modify(int value, EffectContext ctx) {
        if (ctx == null || ctx.targets() == null) {
            return value;
        }
        int armor = ctx.targets().stream().mapToInt(target -> target.armor()).sum();
        return value + perArmor * armor;
    }
}
