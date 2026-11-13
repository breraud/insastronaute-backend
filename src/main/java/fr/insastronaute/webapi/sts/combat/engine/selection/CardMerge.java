package fr.insastronaute.webapi.sts.combat.engine.selection;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardModifier;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.engine.EffectiveCard;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.EnchantmentInstance;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Folds several cards into one.
 *
 * <p>The result is the first selected card wearing everything the others brought: their effects,
 * their scaling, their enchantments and their markers, under a name that lists them all. Its aim
 * widens to the broadest of theirs, so merging a card that hits everyone into one that hits a
 * single target produces a card that hits everyone.
 *
 * <p>Duplicates are dropped on the way in, so merging two identical cards makes one stronger card
 * rather than the same card twice.
 */
public final class CardMerge {

    /**
     * Widest aim first: a merged card is as broad as the broadest card that went into it.
     * ANY_PLAYER sits at the narrow end: it swallows a pure self aim (SELF is unlisted, so it
     * yields to any listed aim), but any enemy-side aim outranks it, matching Unity's merge.
     */
    private static final List<TargetingMode> BREADTH = List.of(
            TargetingMode.ALL_CHARACTERS,
            TargetingMode.ALL_ENEMIES,
            TargetingMode.RANDOM_ENEMY,
            TargetingMode.SINGLE_ENEMY,
            TargetingMode.ANY_PLAYER);

    private CardMerge() {
    }

    /**
     * @param instanceId the identity the merged card is born with, which is a new one
     * @return the merged card, or null when there is nothing to merge
     */
    public static CardInstance merge(
            String instanceId,
            List<CardInstance> selected,
            CardCatalog catalog
    ) {
        if (selected.isEmpty()) {
            return null;
        }
        CardInstance first = selected.getFirst();
        CardDefinition base = catalog.find(first.definitionId());
        if (base == null) {
            return null;
        }

        StringBuilder name = new StringBuilder();
        TargetingMode targeting = base.targetingMode();
        LinkedHashSet<CardTag> tags = new LinkedHashSet<>();
        LinkedHashSet<CardModifier> modifiers = new LinkedHashSet<>();
        LinkedHashSet<EffectDefinition> effects = new LinkedHashSet<>();
        List<EnchantmentInstance> enchantments = new ArrayList<>();

        for (CardInstance instance : selected) {
            CardDefinition card = EffectiveCard.of(instance, catalog.find(instance.definitionId()));
            if (card == null) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append('+');
            }
            name.append(instance.displayName() == null ? card.name() : instance.displayName());
            targeting = broader(targeting, card.targetingMode());
            tags.addAll(instance.tags());
            modifiers.addAll(card.modifiers());
            effects.addAll(card.effects());
            instance.enchantments().forEach(enchantment -> stack(enchantments, enchantment));
        }

        // The base already carries its own effects and modifiers, so only the rest are grafted on.
        effects.removeAll(base.effects());
        modifiers.removeAll(base.modifiers());

        return new CardInstance(
                instanceId,
                base.id(),
                enchantments,
                name.toString(),
                targeting,
                List.copyOf(tags),
                List.copyOf(modifiers),
                List.copyOf(effects));
    }

    private static TargetingMode broader(TargetingMode current, TargetingMode candidate) {
        int currentRank = BREADTH.indexOf(current);
        int candidateRank = BREADTH.indexOf(candidate);
        if (candidateRank < 0) {
            return current;
        }
        if (currentRank < 0) {
            return candidate;
        }
        return candidateRank < currentRank ? candidate : current;
    }

    private static void stack(List<EnchantmentInstance> enchantments, EnchantmentInstance incoming) {
        for (int index = 0; index < enchantments.size(); index++) {
            EnchantmentInstance existing = enchantments.get(index);
            if (existing.enchantmentId().equals(incoming.enchantmentId())) {
                enchantments.set(index, new EnchantmentInstance(
                        existing.enchantmentId(), existing.level() + incoming.level()));
                return;
            }
        }
        enchantments.add(incoming);
    }
}
