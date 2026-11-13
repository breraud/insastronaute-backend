package fr.insastronaute.webapi.sts.combat.engine.enchantment;

import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;
import java.util.List;

public interface EnchantmentHandler {
    String id();
    
    default List<StatModifier> getModifiers(int level) {
        return List.of();
    }
    
    default List<EffectDefinition> getBonusEffects(int level) {
        return List.of();
    }

    /**
     * The share of the damage this card deals that its caster takes back, in percent.
     *
     * <p>Neither a modifier nor a bonus effect can say this: a modifier changes a number before
     * the blow, and a bonus effect is authored with its value fixed, while what is stolen is only
     * known once the damage has been worked out. So the resolver asks after the fact, and an
     * enchantment that steals nothing answers zero.
     */
    default int lifestealPercent(int level) {
        return 0;
    }
}
