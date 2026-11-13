package fr.insastronaute.webapi.sts.combat.engine.enchantment.handlers;

import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FeatherFallingEnchantmentHandler implements EnchantmentHandler {
    @Override
    public String id() {
        return "Légèreté";
    }

    @Override
    public List<EffectDefinition> getBonusEffects(int level) {
        return List.of(
            new EffectDefinition(
                EffectType.ADVANCE_TURN,
                (int) Math.ceil(level * 2f),
                true,
                null,
                0,
                null,
                null
            )
        );
    }
}
