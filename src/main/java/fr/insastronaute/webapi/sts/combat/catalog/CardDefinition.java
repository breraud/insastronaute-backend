package fr.insastronaute.webapi.sts.combat.catalog;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One playable definition, whether it comes from the card catalog or from an inline enemy move.
 *
 * <p>{@code type} and {@code tags} are authored data the card selection filters read, so they
 * are part of the definition rather than something the engine infers.
 */
public record CardDefinition(
        String id,
        String name,
        String favoredCharacter,
        int cost,
        boolean xCost,
        CardType type,
        TargetingMode targetingMode,
        Set<CardTag> tags,
        List<CardModifier> modifiers,
        List<EffectDefinition> effects,
        CardRarity rarity
) {

    /** The shape every caller that does not care how rare the card is still uses. */
    public CardDefinition(
            String id,
            String name,
            String favoredCharacter,
            int cost,
            boolean xCost,
            CardType type,
            TargetingMode targetingMode,
            Set<CardTag> tags,
            List<CardModifier> modifiers,
            List<EffectDefinition> effects
    ) {
        this(id, name, favoredCharacter, cost, xCost, type, targetingMode, tags, modifiers,
                effects, CardRarity.COMMON);
    }

    public CardDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        name = name == null || name.isBlank() ? id : name;
        favoredCharacter = favoredCharacter == null ? "" : favoredCharacter;
        if (cost < 0) {
            throw new IllegalArgumentException("cost must be non-negative");
        }
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(targetingMode, "targetingMode must not be null");
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
        // Unity ships playable cards whose whole behaviour comes from their modifiers or tags,
        // so an empty effect list is authored data, not a broken card.
        effects = List.copyOf(Objects.requireNonNull(effects, "effects must not be null"));
        rarity = rarity == null ? CardRarity.COMMON : rarity;
    }

    public CardDefinition(
            String id,
            String name,
            String favoredCharacter,
            int cost,
            CardType type,
            TargetingMode targetingMode,
            Set<CardTag> tags,
            List<EffectDefinition> effects
    ) {
        this(id, name, favoredCharacter, cost, false, type, targetingMode, tags, List.of(), effects);
    }

    public CardDefinition(
            String id,
            int cost,
            CardType type,
            TargetingMode targetingMode,
            Set<CardTag> tags,
            List<EffectDefinition> effects
    ) {
        this(id, id, "", cost, false, type, targetingMode, tags, List.of(), effects);
    }

    /** Definition whose authored family and tags are unknown, such as a test double. */
    public CardDefinition(
            String id,
            int cost,
            TargetingMode targetingMode,
            List<EffectDefinition> effects
    ) {
        this(id, id, "", cost, false, CardType.NONE, targetingMode, Set.of(), List.of(), effects);
    }

    /**
     * How many times playing this card resolves its effects, before any enchantment says more.
     *
     * <p>An X-cost card spends everything its caster has and resolves once per point, which is
     * the whole of what X means.
     */
    public int replaysFor(int availableEnergy) {
        return xCost ? Math.max(0, availableEnergy) : 1;
    }

    /** @return what playing this card costs its caster, which for an X-cost card is everything */
    public int costFor(int availableEnergy) {
        return xCost ? Math.max(0, availableEnergy) : cost;
    }

    public boolean hasTag(CardTag tag) {
        return tags.contains(tag);
    }
}
