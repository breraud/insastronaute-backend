package fr.insastronaute.webapi.sts.combat.engine.enchantment.handlers;

import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentHandler;
import fr.insastronaute.webapi.sts.combat.model.modifier.PercentModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PunchEnchantmentHandler implements EnchantmentHandler {
    @Override
    public String id() {
        return "Punch";
    }

    @Override
    public List<StatModifier> getModifiers(int level) {
        return List.of(new PercentModifier(StatType.TURN_MANIPULATION_DELAY, level * 10));
    }
}
