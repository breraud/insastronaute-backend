package fr.insastronaute.webapi.sts.combat.engine.enchantment;

import fr.insastronaute.webapi.sts.combat.engine.EffectContext;
import fr.insastronaute.webapi.sts.combat.model.modifier.ModifierType;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;

/** A card enchantment rule that applies after its damage number has been calculated. */
public record DamageRuleModifier(boolean ignoresArmor, boolean floorsDamageAtBase)
        implements StatModifier {

    @Override
    public StatType type() {
        return StatType.ANY;
    }

    @Override
    public ModifierType modifierType() {
        return ModifierType.ADDITIVE;
    }

    @Override
    public int modify(int value, EffectContext context) {
        return value;
    }
}