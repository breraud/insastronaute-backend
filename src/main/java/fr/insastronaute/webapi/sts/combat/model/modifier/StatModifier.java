package fr.insastronaute.webapi.sts.combat.model.modifier;

import fr.insastronaute.webapi.sts.combat.engine.EffectContext;

public interface StatModifier {
    
    StatType type();
    
    ModifierType modifierType();
    
    default boolean appliesTo(StatType stat, EffectContext ctx) {
        return type() == stat || (type() == StatType.ANY && stat != StatType.ANY);
    }
    
    int modify(int value, EffectContext ctx);
}
