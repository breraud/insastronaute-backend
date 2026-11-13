package fr.insastronaute.webapi.sts.combat.engine.enchantment.handlers;

import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentHandler;
import fr.insastronaute.webapi.sts.combat.model.modifier.PercentModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BlessingEnchantmentHandler implements EnchantmentHandler {
    @Override
    public String id() {
        return "Bénédiction";
    }

    @Override
    public List<StatModifier> getModifiers(int level) {
        return List.of(new PercentModifier(StatType.HEAL, level * 10));
    }
}
