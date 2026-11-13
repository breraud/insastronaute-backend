package fr.insastronaute.webapi.sts.combat.engine.enchantment;

import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.EnchantmentInstance;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Spends a budget of enchantment charges on one card.
 *
 * <p>Each round draws an eligible enchantment by weight, then spends part of the budget on it,
 * capped by what that enchantment can still hold. The leftover keeps rolling, so a large budget
 * spreads over several enchantments rather than overfilling one.
 *
 * <p>Every draw goes through the combat's random stream, so an enchanted card is reproduced
 * exactly on replay.
 */
public final class EnchantmentRoller {

    /** Unity rolls the budget of an in-combat enchant between 1 and 14 charges. */
    private static final int MAX_CHARGES = 14;

    /** A single enchantment absorbs at most three charges per round. */
    private static final int MAX_CHARGES_PER_ROUND = 3;

    private EnchantmentRoller() {
    }

    public record Roll(CardInstance instance, List<EnchantmentInstance> applied, RandomState random) {

        public Roll {
            Objects.requireNonNull(instance, "instance");
            applied = List.copyOf(Objects.requireNonNull(applied, "applied"));
            Objects.requireNonNull(random, "random");
        }
    }

    public static Roll roll(CardInstance instance, CardDefinition definition, RandomState random) {
        RandomState.RandomStep budget = random.nextInt(MAX_CHARGES);
        return roll(instance, definition, budget.value() + 1, budget.next());
    }

    public static Roll roll(
            CardInstance instance,
            CardDefinition definition,
            int charges,
            RandomState random
    ) {
        CardInstance current = instance;
        RandomState currentRandom = random;
        List<EnchantmentInstance> applied = new ArrayList<>();
        int remaining = charges;

        while (remaining > 0) {
            List<EnchantmentCatalog.Candidate> candidates =
                    EnchantmentCatalog.candidatesFor(current, definition);
            if (candidates.isEmpty()) {
                break;
            }
            RandomState.RandomStep pick = draw(candidates, currentRandom);
            currentRandom = pick.next();
            EnchantmentCatalog.Candidate candidate = candidates.get(pick.value());

            RandomState.RandomStep share = currentRandom.nextInt(MAX_CHARGES_PER_ROUND);
            currentRandom = share.next();
            int headroom = candidate.maxLevel() - EnchantmentCatalog.levelOf(current, candidate.name());
            int spent = Math.min(Math.min(remaining, headroom), share.value() + 1);
            if (spent <= 0) {
                break;
            }

            current = withEnchantment(current, candidate.name(), spent);
            applied.add(new EnchantmentInstance(candidate.name(), spent));
            remaining -= spent;
        }
        return new Roll(current, applied, currentRandom);
    }

    /** Picks an index into {@code candidates} with a probability proportional to its weight. */
    private static RandomState.RandomStep draw(
            List<EnchantmentCatalog.Candidate> candidates,
            RandomState random
    ) {
        int total = candidates.stream().mapToInt(EnchantmentCatalog.Candidate::weight).sum();
        if (total <= 0) {
            return random.nextInt(candidates.size());
        }
        RandomState.RandomStep step = random.nextInt(total);
        int cumulative = 0;
        for (int index = 0; index < candidates.size(); index++) {
            cumulative += candidates.get(index).weight();
            if (step.value() < cumulative) {
                return new RandomState.RandomStep(index, step.next());
            }
        }
        return new RandomState.RandomStep(candidates.size() - 1, step.next());
    }

    /**
     * The same copy of the card, one enchantment richer. Everything else this copy carries -- a
     * tag, a modifier, a price or a name of its own -- stays: rebuilding the instance from its id
     * and definition alone quietly turned an altered copy back into the authored card.
     */
    private static CardInstance withEnchantment(CardInstance instance, String name, int levels) {
        List<EnchantmentInstance> enchantments = new ArrayList<>(instance.enchantments());
        for (int index = 0; index < enchantments.size(); index++) {
            EnchantmentInstance existing = enchantments.get(index);
            if (name.equals(existing.enchantmentId())) {
                enchantments.set(index,
                        new EnchantmentInstance(name, existing.level() + levels));
                return instance.withEnchantments(enchantments);
            }
        }
        enchantments.add(new EnchantmentInstance(name, levels));
        return instance.withEnchantments(enchantments);
    }
}
