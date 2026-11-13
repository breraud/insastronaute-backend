package fr.insastronaute.webapi.sts.combat.engine.enchantment.handlers;

import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentHandler;
import org.springframework.stereotype.Component;

@Component
public class MechanicalEnchantmentHandler implements EnchantmentHandler {
    @Override
    public String id() {
        return "Mécanique";
    }
}
