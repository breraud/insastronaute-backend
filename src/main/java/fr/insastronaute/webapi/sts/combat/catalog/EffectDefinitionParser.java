package fr.insastronaute.webapi.sts.combat.catalog;

import static fr.insastronaute.webapi.sts.combat.catalog.JsonValues.invalid;
import static fr.insastronaute.webapi.sts.combat.catalog.JsonValues.optionalBoolean;
import static fr.insastronaute.webapi.sts.combat.catalog.JsonValues.optionalInt;
import static fr.insastronaute.webapi.sts.combat.catalog.JsonValues.optionalText;
import static fr.insastronaute.webapi.sts.combat.catalog.JsonValues.optionalTextArray;
import static fr.insastronaute.webapi.sts.combat.catalog.JsonValues.rejectNonEmptyArray;
import static fr.insastronaute.webapi.sts.combat.catalog.JsonValues.rejectUnknownFields;
import static fr.insastronaute.webapi.sts.combat.catalog.JsonValues.requiredInt;
import static fr.insastronaute.webapi.sts.combat.catalog.JsonValues.requiredText;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The single effect vocabulary of the game data.
 *
 * <p>A card and an inline enemy move describe their effects with exactly the same object, so they
 * are read here by exactly the same code. Two adapters would drift, and the one that lags turns
 * an authored enemy move into a different move at runtime.
 */
public final class EffectDefinitionParser {

    /**
     * The only effects that read the selection metadata.
     *
     * <p>Unity serializes {@code cardSelectionSource}, {@code cardSelectionEffect} and
     * {@code cardFilterTags} on every effect, and its authoring tool leaves whatever was last set
     * on the siblings. Its resolver reads them for these three types only, so carrying them
     * anywhere else would invent meaning the reference implementation does not give them.
     */
    private static final Set<EffectType> READ_SELECTION_METADATA = Set.of(
            EffectType.CARD_SELECTION,
            EffectType.ADD_RANDOM_CARD,
            EffectType.ADD_COPY_OF_CARD);

    /**
     * Unity serializes {@code index} on every effect too, but only a status applying itself
     * reads it, as the move-type restriction of a follow-up. Elsewhere it is an unread leftover.
     */
    private static final Set<EffectType> READS_INDEX = Set.of(EffectType.STATUS);

    /** The effects that strike, and so the only ones whose animation says anything. */
    private static final Set<EffectType> CARRIES_ANIMATION =
            Set.of(EffectType.DAMAGE, EffectType.MULTIHIT);

    private static final Set<String> ALLOWED_EFFECT_FIELDS = Set.of(
            "type",
            "value",
            "targetSelf",
            "targetOthers",
            "statusType",
            "duration",
            "description",
            "cardID",
            "conditional",
            "conditionType",
            "conditionValue",
            "trueEffect",
            "cardSelectionSource",
            "index",
            "cardFilterTags",
            "cardSelectionEffect",
            "animationType"
    );

    /** Reads the {@code effects} array of a card or of an enemy move. */
    public List<EffectDefinition> parseEffects(JsonNode ownerNode, String ownerContext) {
        JsonNode effectsNode = ownerNode.get("effects");
        if (effectsNode == null || effectsNode.isNull()) {
            return List.of();
        }
        if (!effectsNode.isArray()) {
            throw invalid(ownerContext + ".effects", "must be an array");
        }

        List<EffectDefinition> effects = new ArrayList<>(effectsNode.size());
        for (int effectIndex = 0; effectIndex < effectsNode.size(); effectIndex++) {
            effects.add(parseEffect(
                    effectsNode.get(effectIndex),
                    ownerContext + ".effects[" + effectIndex + "]"));
        }
        return effects;
    }

    public EffectDefinition parseEffect(JsonNode effectNode, String context) {
        if (effectNode == null || !effectNode.isObject()) {
            throw invalid(context, "effect must be an object");
        }
        rejectUnknownFields(effectNode, ALLOWED_EFFECT_FIELDS, context);

        String rawType = requiredText(effectNode, "type", context);
        EffectType type = EffectType.fromJson(rawType)
                .orElseThrow(() -> invalid(context + ".type", "unsupported value '" + rawType + "'"));

        int value = requiredInt(effectNode, "value", context);
        if (!type.acceptsValue(value)) {
            throw invalid(context + ".value", "must be non-negative");
        }

        boolean readsSelectionMetadata = READ_SELECTION_METADATA.contains(type);
        return new EffectDefinition(
                type,
                value,
                optionalBoolean(effectNode, "targetSelf", context),
                optionalText(effectNode, "statusType", context),
                optionalInt(effectNode, "duration", context),
                optionalText(effectNode, "description", context),
                optionalText(effectNode, "cardID", context),
                readsSelectionMetadata ? parseSelectionSource(effectNode, context) : null,
                readsSelectionMetadata ? parseSelectionAction(effectNode, context) : null,
                readsSelectionMetadata ? parseFilterTags(effectNode, context) : List.of(),
                optionalBoolean(effectNode, "targetOthers", context),
                optionalBoolean(effectNode, "trueEffect", context),
                parseCondition(effectNode, context),
                READS_INDEX.contains(type) ? optionalInt(effectNode, "index", context) : 0,
                parseAnimationType(effectNode, context));
    }

    /**
     * A blow's flavour, which only a blow has.
     *
     * <p>Unity writes {@code animationType} on every effect it serializes, healing and card draw
     * included, and there it means nothing. Reading it only for the effects that strike keeps the
     * rest at {@link AnimationType#DEFAULT} rather than recording a flavour no damage will ever
     * wear.
     */
    private static AnimationType parseAnimationType(JsonNode effectNode, String context) {
        if (!CARRIES_ANIMATION.contains(EffectType.fromJson(
                effectNode.get("type").asText()).orElse(null))) {
            return AnimationType.DEFAULT;
        }
        String raw = optionalText(effectNode, "animationType", context);
        if (raw == null || raw.isBlank()) {
            return AnimationType.DEFAULT;
        }
        return AnimationType.fromJson(raw).orElseThrow(() -> invalid(
                context + ".animationType", "unsupported value '" + raw + "'"));
    }

    private static CardSelectionSource parseSelectionSource(JsonNode effectNode, String context) {
        String raw = optionalText(effectNode, "cardSelectionSource", context);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return CardSelectionSource.fromJson(raw).orElseThrow(() -> invalid(
                context + ".cardSelectionSource", "unsupported value '" + raw + "'"));
    }

    private static CardSelectionAction parseSelectionAction(JsonNode effectNode, String context) {
        String raw = optionalText(effectNode, "cardSelectionEffect", context);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return CardSelectionAction.fromJson(raw).orElseThrow(() -> invalid(
                context + ".cardSelectionEffect", "unsupported value '" + raw + "'"));
    }

    private static List<CardFilterTag> parseFilterTags(JsonNode effectNode, String context) {
        List<String> raw = optionalTextArray(effectNode, "cardFilterTags", context);
        List<CardFilterTag> tags = new ArrayList<>(raw.size());
        for (int index = 0; index < raw.size(); index++) {
            String value = raw.get(index);
            int position = index;
            tags.add(CardFilterTag.fromJson(value).orElseThrow(() -> invalid(
                    context + ".cardFilterTags[" + position + "]",
                    "unsupported value '" + value + "'")));
        }
        return List.copyOf(tags);
    }

    /**
     * Reads the gate on an effect.
     *
     * <p>Unity writes a condition type on every effect and only honours it when {@code conditional}
     * is set, so an unset flag means there is no gate at all, whatever the type says.
     */
    private static EffectCondition parseCondition(JsonNode effectNode, String context) {
        if (!optionalBoolean(effectNode, "conditional", context)) {
            return null;
        }
        String raw = optionalText(effectNode, "conditionType", context);
        ConditionType type = ConditionType.fromJson(raw).orElseThrow(() -> invalid(
                context + ".conditionType", "unsupported value '" + raw + "'"));
        return new EffectCondition(type, optionalText(effectNode, "conditionValue", context));
    }
}
