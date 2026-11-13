package fr.insastronaute.webapi.sts.combat.engine.enchantment.handlers;

import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentHandler;
import fr.insastronaute.webapi.sts.combat.engine.enchantment.DamageRuleModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HumanismEnchantmentHandler implements EnchantmentHandler {
    @Override
    public String id() {
        return "Humanisme";
    }

    @Override
    public List<StatModifier> getModifiers(int level) {
        return List.of(new DamageRuleModifier(true, false));
    }
}
