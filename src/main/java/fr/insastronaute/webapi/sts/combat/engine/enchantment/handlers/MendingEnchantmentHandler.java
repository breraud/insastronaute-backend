package fr.insastronaute.webapi.sts.combat.engine.enchantment.handlers;

import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentHandler;
import org.springframework.stereotype.Component;

@Component
public class MendingEnchantmentHandler implements EnchantmentHandler {
    @Override
    public String id() {
        return "Mending";
    }
}
