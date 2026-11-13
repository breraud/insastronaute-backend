package fr.insastronaute.webapi.sts.combat.engine.enchantment.handlers;

import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KnockbackEnchantmentHandler implements EnchantmentHandler {
    @Override
    public String id() {
        return "Recul";
    }

    @Override
    public List<EffectDefinition> getBonusEffects(int level) {
        return List.of(
            new EffectDefinition(
                EffectType.DELAY_TURN,
                (int) Math.ceil(level * 2f),
                false,
                null,
                0,
                null,
                null
            )
        );
    }
}
