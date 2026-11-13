package fr.insastronaute.webapi.sts.combat.engine.enchantment.handlers;

import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FlameEnchantmentHandler implements EnchantmentHandler {

    @Override
    public String id() {
        return "Aura de feu";
    }

    /**
     * « Inflige Brulure a la cible en fonction du niveau » (FlameEnchantment.cs).
     *
     * <p>Le nom du statut se posait sur le mauvais parametre : la signature est
     * {@code (type, value, targetSelf, statusType, duration, description, cardId)}, et
     * « BURN » arrivait en septieme position, celle de la carte. L'effet partait donc avec un
     * {@code statusType} nul — un Statut sans statut — et ne brulait personne. Le catalogue
     * l'ecrit « Burn », pas « BURN ».
     */
    @Override
    public List<EffectDefinition> getBonusEffects(int level) {
        return List.of(
                new EffectDefinition(
                        EffectType.STATUS,
                        5,
                        false,
                        "Burn",
                        level,
                        null,
                        null
                )
        );
    }
}
