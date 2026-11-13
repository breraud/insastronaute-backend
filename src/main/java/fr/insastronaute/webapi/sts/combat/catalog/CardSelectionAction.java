package fr.insastronaute.webapi.sts.combat.catalog;

import java.util.Optional;

/**
 * What happens to the cards a {@code CardSelection} selected, mirroring Unity's
 * {@code CardSelectionEffect}.
 *
 * <p>Which of these the engine can actually resolve is a separate question, answered by
 * {@link CardSelectionSupport}: the parser accepts every value Unity can author, because a
 * dropped action is a silently different combat.
 */
public enum CardSelectionAction {

    NONE("None"),
    EXHAUST("Exhaust"),
    DISCARD("Discard"),
    RETURN_TO_HAND("ReturnToHand"),
    ENCHANT("Enchant"),
    UNENCHANT("Unenchant"),
    TRANSFORM("Transform"),
    MERGE("Merge"),
    TOP_OF_DRAW_PILE("TopOfDrawPile"),
    CONSUME_AND_DEAL_DAMAGE_TO_ALL("ConsumeAndDealDamageToAll"),
    CONSUME_AND_GAIN_ARMOR("ConsumeAndGainArmor"),
    REDUCE_COST("ReduceCost"),
    REDUCE_COST_TEMP("ReduceCostTemp"),
    INCREASE_DAMAGE("IncreaseDamage"),
    INCREASE_DAMAGE_TEMP("IncreaseDamageTemp"),
    ADD_DAMAGE_EFFECT("AddDamageEffect"),
    ADD_ARMOR_EFFECT("AddArmorEffect"),
    ADD_EXHAUST_TAG("AddExhaustTag"),
    ADD_AUTOMATIC_TAG("AddAutomaticTag"),
    ADD_RETAIN_TAG("AddRetainTag"),
    REMOVE_EXHAUST_TAG("RemoveExhaustTag"),
    REMOVE_ETHEREAL_TAG("RemoveEtherealTag"),
    REMOVE_EXHAUST_AND_ETHEREAL_TAGS("RemoveExhaustAndEtherealTags"),
    COPY_TO_HAND("CopyToHand");

    private final String jsonValue;

    CardSelectionAction(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    public String jsonValue() {
        return jsonValue;
    }

    public static Optional<CardSelectionAction> fromJson(String value) {
        for (CardSelectionAction action : values()) {
            if (action.jsonValue.equals(value)) {
                return Optional.of(action);
            }
        }
        return Optional.empty();
    }
}
