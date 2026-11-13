package fr.insastronaute.webapi.sts.combat.engine.enchantment;

import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.model.EnchantmentInstance;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class EnchantmentEngine {

    private final Map<String, EnchantmentHandler> handlers;

    public EnchantmentEngine(List<EnchantmentHandler> handlerList) {
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(EnchantmentHandler::id, Function.identity()));
    }

    public List<StatModifier> getModifiers(List<EnchantmentInstance> enchantments) {
        if (enchantments == null) return List.of();
        return enchantments.stream()
                .filter(e -> handlers.containsKey(e.enchantmentId()))
                .flatMap(e -> handlers.get(e.enchantmentId()).getModifiers(e.level()).stream())
                .toList();
    }

    /** What every enchantment on a card steals back from the damage it deals, added together. */
    public int lifestealPercent(List<EnchantmentInstance> enchantments) {
        if (enchantments == null) return 0;
        return enchantments.stream()
                .filter(e -> handlers.containsKey(e.enchantmentId()))
                .mapToInt(e -> handlers.get(e.enchantmentId()).lifestealPercent(e.level()))
                .sum();
    }

    public List<EffectDefinition> getBonusEffects(List<EnchantmentInstance> enchantments) {
        if (enchantments == null) return List.of();
        return enchantments.stream()
                .filter(e -> handlers.containsKey(e.enchantmentId()))
                .flatMap(e -> handlers.get(e.enchantmentId()).getBonusEffects(e.level()).stream())
                .toList();
    }
}
