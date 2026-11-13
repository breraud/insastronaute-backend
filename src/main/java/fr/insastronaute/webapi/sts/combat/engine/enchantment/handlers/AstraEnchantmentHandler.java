package fr.insastronaute.webapi.sts.combat.engine.enchantment.handlers;

import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentHandler;
import fr.insastronaute.webapi.sts.combat.model.modifier.FlatModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.OneModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AstraEnchantmentHandler implements EnchantmentHandler {
    @Override
    public String id() {
        return "Stellaire";
    }

    @Override
    public List<StatModifier> getModifiers(int level) {
        return List.of(
            new OneModifier(StatType.ANY),
            new FlatModifier(StatType.REPLAY_COUNT, 4)
        );
    }
}
