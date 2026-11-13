package fr.insastronaute.webapi.sts.combat.engine.enchantment;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Which enchantments a card may receive, how likely each is, and how far it can be pushed.
 *
 * <p>Enchanting is not a uniform draw: an enchantment offers itself only to a card it makes sense
 * on, so armor enchantments need a card that grants armor and attack enchantments need an attack.
 * The weights and the ceilings are the ones authored in Unity.
 */
public final class EnchantmentCatalog {

    /**
     * @param name     the identifier carried by {@code EnchantmentInstance} and by the handlers
     * @param maxLevel how much of it a single card can hold
     * @param weight   relative likelihood, in hundredths, of being drawn among the eligible ones
     */
    public record Candidate(String name, int maxLevel, int weight) {

        public Candidate {
            Objects.requireNonNull(name, "name");
        }
    }

    public static final String MECHANICAL = "Mécanique";
    public static final String INFINITY = "Infinity";
    public static final String TELEKINESIS = "Télékinésie";
    public static final String HUMANISM = "Humanisme";
    public static final String ECHO = "Écho";
    public static final String ASTRA = "Stellaire";

    private EnchantmentCatalog() {
    }

    /** Enchantments this instance may still receive, in a stable order. */
    public static List<Candidate> candidatesFor(CardInstance instance, CardDefinition definition) {
        List<Candidate> candidates = new ArrayList<>();
        if (!has(instance, MECHANICAL) && !has(instance, INFINITY)) {
            candidates.add(new Candidate(MECHANICAL, 1, 10));
        }
        if (definition.type() == CardType.ATTACK) {
            candidates.add(new Candidate("Power", 100, 200));
            candidates.add(new Candidate("Lifesteal", 100, 30));
            candidates.add(new Candidate("Aura de feu", 5, 50));
            candidates.add(new Candidate("Transpercement", 5, 50));
            if (!has(instance, TELEKINESIS)) {
                candidates.add(new Candidate(TELEKINESIS, 1, 50));
            }
            if (!has(instance, HUMANISM)) {
                candidates.add(new Candidate(HUMANISM, 1, 50));
            }
        }
        if (hasEffect(definition, EffectType.DELAY_TURN)) {
            candidates.add(new Candidate("Punch", 100, 60));
        }
        if (!hasEffect(definition, EffectType.ADVANCE_TURN)) {
            candidates.add(new Candidate("Légèreté", 100, 60));
        }
        if (hasEffect(definition, EffectType.ARMOR)) {
            candidates.add(new Candidate("Protection", 100, 200));
        }
        if (definition.effects().stream()
                .anyMatch(effect -> effect.type() == EffectType.STATUS && effect.duration() > 0)) {
            candidates.add(new Candidate("Extension", 5, 50));
        }
        if (definition.effects().stream()
                .anyMatch(effect -> effect.type() == EffectType.STATUS && effect.value() > 0)) {
            candidates.add(new Candidate("Efficacité", 5, 50));
        }
        if (definition.hasTag(CardTag.EXHAUST)) {
            candidates.add(new Candidate("Mending", 1, 20));
            candidates.add(new Candidate("Unbreaking", 10, 100));
            candidates.add(new Candidate("Malédiction de disparition", 10, 50));
        } else if (!has(instance, INFINITY) && !has(instance, MECHANICAL)
                && !definition.hasTag(CardTag.INFINITE)
                && !definition.xCost()
                && definition.type() != CardType.POWER) {
            candidates.add(new Candidate(INFINITY, 1, 50));
        }
        if (definition.targetingMode() == TargetingMode.ALL_ENEMIES
                || definition.targetingMode() == TargetingMode.ALL_CHARACTERS) {
            candidates.add(new Candidate("Sweeping Edge", 3, 50));
        }
        if (definition.targetingMode() == TargetingMode.SINGLE_ENEMY
                || definition.targetingMode() == TargetingMode.RANDOM_ENEMY) {
            if (!hasEffect(definition, EffectType.DAMAGE)) {
                candidates.add(new Candidate("Sharpness", 100, 100));
            }
            if (!hasEffect(definition, EffectType.DELAY_TURN)) {
                candidates.add(new Candidate("Recul", 100, 60));
            }
        }
        if (!has(instance, ASTRA)) {
            candidates.add(new Candidate(ECHO, 10, 5));
        }
        return List.copyOf(candidates);
    }

    public static int levelOf(CardInstance instance, String name) {
        return instance.enchantments().stream()
                .filter(enchantment -> name.equals(enchantment.enchantmentId()))
                .mapToInt(fr.insastronaute.webapi.sts.combat.model.EnchantmentInstance::level)
                .findFirst()
                .orElse(0);
    }

    private static boolean has(CardInstance instance, String name) {
        return levelOf(instance, name) > 0;
    }

    private static boolean hasEffect(CardDefinition definition, EffectType type) {
        return definition.effects().stream().anyMatch(effect -> effect.type() == type);
    }
}
