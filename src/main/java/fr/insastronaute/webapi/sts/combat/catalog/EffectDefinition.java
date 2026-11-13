package fr.insastronaute.webapi.sts.combat.catalog;

import java.util.List;
import java.util.Objects;

/**
 * One authored effect of a card or of an enemy move.
 *
 * <p>{@code targetSelf} belongs to the effect, never to the card: a move that armours its caster
 * and damages its opponent must keep both halves, so nothing here may be widened to the card's
 * targeting mode.
 */
public record EffectDefinition(
        EffectType type,
        int value,
        boolean targetSelf,
        String statusType,
        int duration,
        String description,
        String cardId,
        CardSelectionSource cardSelectionSource,
        CardSelectionAction cardSelectionAction,
        List<CardFilterTag> cardFilterTags,
        boolean targetOthers,
        boolean trueEffect,
        EffectCondition condition,
        int index,
        AnimationType animationType
) {

    public EffectDefinition {
        Objects.requireNonNull(type, "type must not be null");
        if (!type.acceptsValue(value)) {
            throw new IllegalArgumentException("value is invalid for effect type " + type);
        }
        cardFilterTags = cardFilterTags == null ? List.of() : List.copyOf(cardFilterTags);
        // An unauthored flavour is the plain one, so every constructor that predates animation
        // may keep its arity and still hand back an effect whose flavour can be read.
        animationType = animationType == null ? AnimationType.DEFAULT : animationType;
    }

    /**
     * The former canonical shape, before a blow's flavour became something the engine reads.
     *
     * <p>Kept so the constructors below -- and the tests built on them -- stay as they were: an
     * effect nobody gave a flavour to is a plain one.
     */
    public EffectDefinition(
            EffectType type,
            int value,
            boolean targetSelf,
            String statusType,
            int duration,
            String description,
            String cardId,
            CardSelectionSource cardSelectionSource,
            CardSelectionAction cardSelectionAction,
            List<CardFilterTag> cardFilterTags,
            boolean targetOthers,
            boolean trueEffect,
            EffectCondition condition,
            int index
    ) {
        this(type, value, targetSelf, statusType, duration, description, cardId,
                cardSelectionSource, cardSelectionAction, cardFilterTags, targetOthers, trueEffect,
                condition, index, AnimationType.DEFAULT);
    }

    public EffectDefinition(
            EffectType type,
            int value,
            boolean targetSelf,
            String statusType,
            int duration,
            String description,
            String cardId,
            CardSelectionSource cardSelectionSource,
            CardSelectionAction cardSelectionAction,
            List<CardFilterTag> cardFilterTags,
            boolean targetOthers,
            boolean trueEffect,
            EffectCondition condition
    ) {
        this(type, value, targetSelf, statusType, duration, description, cardId,
                cardSelectionSource, cardSelectionAction, cardFilterTags, targetOthers, trueEffect,
                condition, 0);
    }

    public EffectDefinition(
            EffectType type,
            int value,
            boolean targetSelf,
            String statusType,
            int duration,
            String description,
            String cardId,
            CardSelectionSource cardSelectionSource,
            CardSelectionAction cardSelectionAction,
            List<CardFilterTag> cardFilterTags
    ) {
        this(type, value, targetSelf, statusType, duration, description, cardId,
                cardSelectionSource, cardSelectionAction, cardFilterTags, false, false, null, 0);
    }

    public EffectDefinition(
            EffectType type,
            int value,
            boolean targetSelf,
            String statusType,
            int duration,
            String description,
            String cardId
    ) {
        this(type, value, targetSelf, statusType, duration, description, cardId, null, null, List.of());
    }

    public EffectDefinition(EffectType type, int value, boolean targetSelf) {
        this(type, value, targetSelf, null, 0, null, null);
    }

    public EffectDefinition(EffectType type, int value) {
        this(type, value, false);
    }

    /**
     * The same effect, carrying two different numbers.
     *
     * <p>For whoever changes what an effect is worth after it was authored but before it lands --
     * Chaos doubling what its bearer sends the other way. Everything else about the effect is what
     * it was: the same target, the same condition, the same card it names.
     */
    public EffectDefinition withValueAndDuration(int newValue, int newDuration) {
        return new EffectDefinition(
                type, newValue, targetSelf, statusType, newDuration, description, cardId,
                cardSelectionSource, cardSelectionAction, cardFilterTags, targetOthers, trueEffect,
                condition, index, animationType);
    }

    /** @return the selection action, or {@link CardSelectionAction#NONE} when none is authored */
    public CardSelectionAction selectionAction() {
        return cardSelectionAction == null ? CardSelectionAction.NONE : cardSelectionAction;
    }

    /**
     * Named the way a bean getter is named, so Jackson would otherwise write it into the saved
     * combat as a "conditional" property and choke on it when reading that combat back.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isConditional() {
        return condition != null;
    }
}
