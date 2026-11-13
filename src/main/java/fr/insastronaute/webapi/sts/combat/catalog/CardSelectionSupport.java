package fr.insastronaute.webapi.sts.combat.catalog;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Single source of truth for which {@code CardSelection} combinations the engine can actually
 * resolve.
 *
 * <p>The parser accepts every combination Unity declares, because silently dropping selection
 * metadata turns a visible error into a wrong combat. Playability is therefore a separate,
 * explicit question, asked here by both the command validation path and the data compatibility
 * scanner so the two can never disagree.
 */
public final class CardSelectionSupport {

    public static final Set<CardSelectionSource> IMPLEMENTED_SOURCES =
            EnumSet.allOf(CardSelectionSource.class);

    public static final Set<CardSelectionAction> IMPLEMENTED_ACTIONS = EnumSet.of(
            CardSelectionAction.EXHAUST,
            CardSelectionAction.DISCARD,
            CardSelectionAction.RETURN_TO_HAND,
            CardSelectionAction.TOP_OF_DRAW_PILE,
            CardSelectionAction.CONSUME_AND_GAIN_ARMOR,
            CardSelectionAction.CONSUME_AND_DEAL_DAMAGE_TO_ALL,
            CardSelectionAction.TRANSFORM,
            CardSelectionAction.ENCHANT,
            CardSelectionAction.MERGE,
            CardSelectionAction.REDUCE_COST,
            CardSelectionAction.REDUCE_COST_TEMP,
            CardSelectionAction.INCREASE_DAMAGE,
            CardSelectionAction.INCREASE_DAMAGE_TEMP,
            CardSelectionAction.ADD_DAMAGE_EFFECT,
            CardSelectionAction.ADD_ARMOR_EFFECT,
            CardSelectionAction.ADD_EXHAUST_TAG,
            CardSelectionAction.ADD_AUTOMATIC_TAG,
            CardSelectionAction.ADD_RETAIN_TAG,
            CardSelectionAction.REMOVE_EXHAUST_TAG,
            CardSelectionAction.REMOVE_ETHEREAL_TAG,
            CardSelectionAction.REMOVE_EXHAUST_AND_ETHEREAL_TAGS,
            CardSelectionAction.COPY_TO_HAND,
            CardSelectionAction.UNENCHANT);

    private CardSelectionSupport() {
    }

    /**
     * @return why this selection cannot be resolved yet, or empty when it is fully implemented
     */
    public static Optional<String> unsupportedReason(EffectDefinition effect) {
        if (effect.type() != EffectType.CARD_SELECTION) {
            return Optional.empty();
        }
        CardSelectionSource source = effect.cardSelectionSource();
        if (source == null) {
            return Optional.of("CardSelection has no source");
        }
        if (!IMPLEMENTED_SOURCES.contains(source)) {
            return Optional.of("CardSelection source '" + source.jsonValue() + "' is not implemented");
        }
        CardSelectionAction action = effect.cardSelectionAction();
        if (action == null || action == CardSelectionAction.NONE) {
            return Optional.of("CardSelection has no action");
        }
        if (!IMPLEMENTED_ACTIONS.contains(action)) {
            return Optional.of("CardSelection action '" + action.jsonValue() + "' is not implemented");
        }
        return Optional.empty();
    }
}
