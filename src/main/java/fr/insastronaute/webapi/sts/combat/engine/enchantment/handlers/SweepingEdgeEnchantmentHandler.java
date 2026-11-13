package fr.insastronaute.webapi.sts.combat.engine.enchantment.handlers;

import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentHandler;
import fr.insastronaute.webapi.sts.combat.model.modifier.TargetNumberModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SweepingEdgeEnchantmentHandler implements EnchantmentHandler {
    @Override
    public String id() {
        return "Sweeping Edge";
    }

    @Override
    public List<StatModifier> getModifiers(int level) {
        return List.of(new TargetNumberModifier(StatType.DAMAGE, level * 10));
    }
}
