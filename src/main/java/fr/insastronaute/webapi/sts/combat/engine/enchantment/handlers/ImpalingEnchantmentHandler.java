package fr.insastronaute.webapi.sts.combat.engine.enchantment.handlers;

import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentHandler;
import fr.insastronaute.webapi.sts.combat.model.modifier.ArmorModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ImpalingEnchantmentHandler implements EnchantmentHandler {
    @Override
    public String id() {
        return "Transpercement";
    }

    @Override
    public List<StatModifier> getModifiers(int level) {
        return List.of(new ArmorModifier(StatType.DAMAGE, level * 1));
    }
}
