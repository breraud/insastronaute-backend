package fr.insastronaute.webapi.sts.combat.engine.enchantment.handlers;

import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentHandler;
import fr.insastronaute.webapi.sts.combat.engine.EffectContext;
import fr.insastronaute.webapi.sts.combat.model.modifier.FlatModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.ModifierType;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReplayEnchantmentHandler implements EnchantmentHandler {
    @Override
    public String id() {
        return "Écho";
    }

    @Override
    public List<StatModifier> getModifiers(int level) {
        int percentage = switch (level) {
            case 1 -> -50;
            case 2 -> -67;
            case 3 -> -75;
            case 4 -> -80;
            case 5 -> -83;
            case 6 -> -85;
            case 7 -> -86;
            case 8 -> -88;
            case 9 -> -89;
            case 10 -> -90;
            default -> -100;
        };
        
        return List.of(
            new EffectReductionModifier(percentage),
            new FlatModifier(StatType.REPLAY_COUNT, level)
        );
    }

    private record EffectReductionModifier(int percentage) implements StatModifier {
        @Override
        public StatType type() {
            return StatType.ANY;
        }

        @Override
        public ModifierType modifierType() {
            return ModifierType.MULTIPLICATIVE;
        }

        @Override
        public boolean appliesTo(StatType stat, EffectContext context) {
            return stat != StatType.REPLAY_COUNT && StatModifier.super.appliesTo(stat, context);
        }

        @Override
        public int modify(int value, EffectContext context) {
            return value + (value * percentage / 100);
        }
    }
}
