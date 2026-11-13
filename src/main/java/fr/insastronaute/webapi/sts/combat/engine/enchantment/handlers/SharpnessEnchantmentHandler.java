package fr.insastronaute.webapi.sts.combat.engine.enchantment.handlers;

import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SharpnessEnchantmentHandler implements EnchantmentHandler {

    @Override
    public String id() {
        return "Sharpness";
    }

    @Override
    public List<EffectDefinition> getBonusEffects(int level) {
        return List.of(
                new EffectDefinition(
                        EffectType.DAMAGE,
                        level,
                        false,
                        null,
                        0,
                        null,
                        null
                )
        );
    }
}
