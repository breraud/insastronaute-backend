package fr.insastronaute.webapi.sts.combat.engine.enchantment.handlers;

import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentHandler;
import org.springframework.stereotype.Component;

/**
 * Vol de vie : « Rend 5% des dégâts infligés par niveau. »
 *
 * <p>Le handler était vide, donc l'enchantement se posait sur la carte, s'y affichait, et ne
 * rendait jamais rien.
 */
@Component
public class LifestealEnchantmentHandler implements EnchantmentHandler {

    /** LifestealEnchantment.cs:3. */
    private static final int PERCENT_PER_LEVEL = 5;

    @Override
    public String id() {
        return "Lifesteal";
    }

    @Override
    public int lifestealPercent(int level) {
        return Math.max(0, level) * PERCENT_PER_LEVEL;
    }
}
