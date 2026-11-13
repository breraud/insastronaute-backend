package fr.insastronaute.webapi.sts.combat.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import fr.insastronaute.webapi.sts.combat.catalog.CardModifier;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import java.util.List;

/**
 * One card in a combat, as opposed to the definition it was made from.
 *
 * <p>Most instances are just a definition with an identity. The rest carry what happened to them
 * during the fight: enchantments, a cost the combat reduced, an effect a card grafted onto them,
 * or the merged identity of several cards folded into one. Those overrides sit here rather than
 * in the catalog because they belong to this copy of the card and to no other.
 *
 * @param displayName    the merged or renamed title, null when the definition's name stands
 * @param targetingMode  an aim that replaces the definition's, null when it stands
 * @param tags           markers added on top of the definition's
 * @param modifiers      scaling rules added on top of the definition's
 * @param addedEffects   effects grafted after the definition's own
 * @param cardType       a family that replaces the definition's, null when it stands
 * @param cost           what this copy costs regardless of the definition, null when it stands
 * @param removedTags    markers stripped from the definition's for this copy alone
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CardInstance(
        String instanceId,
        String definitionId,
        List<EnchantmentInstance> enchantments,
        String displayName,
        TargetingMode targetingMode,
        List<CardTag> tags,
        List<CardModifier> modifiers,
        List<EffectDefinition> addedEffects,
        CardType cardType,
        Integer cost,
        List<CardTag> removedTags
) {

    /** The shape every caller that never strips an authored tag from this copy still uses. */
    public CardInstance(
            String instanceId,
            String definitionId,
            List<EnchantmentInstance> enchantments,
            String displayName,
            TargetingMode targetingMode,
            List<CardTag> tags,
            List<CardModifier> modifiers,
            List<EffectDefinition> addedEffects,
            CardType cardType,
            Integer cost
    ) {
        this(instanceId, definitionId, enchantments, displayName, targetingMode, tags, modifiers,
                addedEffects, cardType, cost, List.of());
    }

    /**
     * The shape every caller but a card conjured out of another one still uses.
     *
     * <p>A copy that wears its own family and price is the exception; everything else on the
     * table is a definition with an identity and, at most, what the fight did to it.
     */
    public CardInstance(
            String instanceId,
            String definitionId,
            List<EnchantmentInstance> enchantments,
            String displayName,
            TargetingMode targetingMode,
            List<CardTag> tags,
            List<CardModifier> modifiers,
            List<EffectDefinition> addedEffects
    ) {
        this(instanceId, definitionId, enchantments, displayName, targetingMode, tags, modifiers,
                addedEffects, null, null, List.of());
    }

    public CardInstance(String instanceId, String definitionId) {
        this(instanceId, definitionId, List.of());
    }

    public CardInstance(
            String instanceId,
            String definitionId,
            List<EnchantmentInstance> enchantments
    ) {
        this(instanceId, definitionId, enchantments, null, null, List.of(), List.of(), List.of());
    }

    public CardInstance {
        requireNonBlank(instanceId, "instanceId");
        requireNonBlank(definitionId, "definitionId");
        enchantments = enchantments == null ? List.of() : List.copyOf(enchantments);
        tags = tags == null ? List.of() : List.copyOf(tags);
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
        addedEffects = addedEffects == null ? List.of() : List.copyOf(addedEffects);
        removedTags = removedTags == null ? List.of() : List.copyOf(removedTags);
        displayName = displayName == null || displayName.isBlank() ? null : displayName;
        if (cost != null && cost < 0) {
            throw new IllegalArgumentException("cost must be non-negative");
        }
    }

    /** @return true when this copy behaves exactly like the definition it was made from */
    @JsonIgnore
    public boolean isPlain() {
        return displayName == null
                && targetingMode == null
                && tags.isEmpty()
                && modifiers.isEmpty()
                && addedEffects.isEmpty()
                && cardType == null
                && cost == null
                && removedTags.isEmpty();
    }

    /** @return the family this copy answers to, which is the definition's unless it overrides it */
    public CardType typeOr(CardType definitionType) {
        return cardType == null ? definitionType : cardType;
    }

    /** @return what this copy costs, which is the definition's price unless it overrides it */
    public int costOr(int definitionCost) {
        return cost == null ? definitionCost : cost;
    }

    public CardInstance withEnchantments(List<EnchantmentInstance> enchantments) {
        return new CardInstance(instanceId, definitionId, enchantments, displayName,
                targetingMode, tags, modifiers, addedEffects, cardType, cost, removedTags);
    }

    public CardInstance withModifier(CardModifier modifier) {
        List<CardModifier> added = new java.util.ArrayList<>(modifiers);
        added.add(modifier);
        return new CardInstance(instanceId, definitionId, enchantments, displayName,
                targetingMode, tags, added, addedEffects, cardType, cost, removedTags);
    }

    public CardInstance withAddedEffect(EffectDefinition effect) {
        List<EffectDefinition> added = new java.util.ArrayList<>(addedEffects);
        added.add(effect);
        return new CardInstance(instanceId, definitionId, enchantments, displayName,
                targetingMode, tags, modifiers, added, cardType, cost, removedTags);
    }

    public CardInstance withTargetingMode(TargetingMode targetingMode) {
        return new CardInstance(instanceId, definitionId, enchantments, displayName,
                targetingMode, tags, modifiers, addedEffects, cardType, cost, removedTags);
    }

    public CardInstance withTag(CardTag tag) {
        if (tags.contains(tag) && !removedTags.contains(tag)) {
            return this;
        }
        List<CardTag> added = new java.util.ArrayList<>(tags);
        if (!added.contains(tag)) {
            added.add(tag);
        }
        List<CardTag> unremoved = removedTags.isEmpty() ? removedTags : new java.util.ArrayList<>(removedTags);
        if (!unremoved.isEmpty()) {
            unremoved.remove(tag);
        }
        return new CardInstance(instanceId, definitionId, enchantments, displayName,
                targetingMode, added, modifiers, addedEffects, cardType, cost, unremoved);
    }

    /**
     * Strips an authored or added marker from this copy alone, whichever side of
     * {@link fr.insastronaute.webapi.sts.combat.engine.EffectiveCard} it came from.
     */
    public CardInstance withoutTag(CardTag tag) {
        if (removedTags.contains(tag) && !tags.contains(tag)) {
            return this;
        }
        List<CardTag> kept = tags.isEmpty() ? tags : new java.util.ArrayList<>(tags);
        if (!kept.isEmpty()) {
            kept.remove(tag);
        }
        List<CardTag> removed = new java.util.ArrayList<>(removedTags);
        if (!removed.contains(tag)) {
            removed.add(tag);
        }
        return new CardInstance(instanceId, definitionId, enchantments, displayName,
                targetingMode, kept, modifiers, addedEffects, cardType, cost, removed);
    }

    /** Drops every temporary scaling rule, at the moment the turn that granted them ends. */
    public CardInstance withoutTemporaryModifiers() {
        List<CardModifier> lasting = modifiers.stream()
                .filter(modifier -> !modifier.temporary())
                .toList();
        return lasting.size() == modifiers.size()
                ? this
                : new CardInstance(instanceId, definitionId, enchantments, displayName,
                        targetingMode, tags, lasting, addedEffects, cardType, cost, removedTags);
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
