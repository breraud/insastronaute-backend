package fr.insastronaute.webapi.sts.combat.catalog;

import static fr.insastronaute.webapi.sts.combat.catalog.JsonValues.invalid;
import static fr.insastronaute.webapi.sts.combat.catalog.JsonValues.optionalText;
import static fr.insastronaute.webapi.sts.combat.catalog.JsonValues.optionalTextArray;
import static fr.insastronaute.webapi.sts.combat.catalog.JsonValues.rejectUnknownFields;
import static fr.insastronaute.webapi.sts.combat.catalog.JsonValues.requiredNonNegativeInt;
import static fr.insastronaute.webapi.sts.combat.catalog.JsonValues.requiredText;

import com.fasterxml.jackson.databind.JsonNode;
import fr.insastronaute.webapi.sts.combat.model.modifier.ModifierKind;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CardCatalogParser {

    private static final Set<String> ALLOWED_ROOT_FIELDS = Set.of("dataVersion", "cards");

    private static final Set<String> ALLOWED_MODIFIER_FIELDS = Set.of(
            "type", "kind", "value", "info", "description");

    private static final Set<String> ALLOWED_CARD_FIELDS = Set.of(
            "id",
            "cardName",
            "collectionCardId",
            "multiplayerExclusive",
            "pveOnly",
            "characterLevel",
            "iconId",
            "cost",
            "xCost",
            "type",
            "rarity",
            "targetingMode",
            "effects",
            "modifiers",
            "favoredCharacter",
            "animationSpeed",
            "startingCount",
            "tags"
    );

    private final EffectDefinitionParser effectParser = new EffectDefinitionParser();

    public CardCatalog parse(JsonNode root) {
        if (root == null || root.isNull() || !root.isObject()) {
            throw invalid("root", "must be an object");
        }
        rejectUnknownFields(root, ALLOWED_ROOT_FIELDS, "root");
        String dataVersion = requiredText(root, "dataVersion", "root");

        JsonNode cardsNode = root.get("cards");
        if (cardsNode == null || !cardsNode.isArray()) {
            throw invalid("cards", "must be an array");
        }

        Map<String, CardDefinition> cards = new LinkedHashMap<>();
        for (int cardIndex = 0; cardIndex < cardsNode.size(); cardIndex++) {
            String indexContext = "cards[" + cardIndex + "]";
            CardDefinition definition = parseCard(cardsNode.get(cardIndex), indexContext);
            if (cards.putIfAbsent(definition.id(), definition) != null) {
                throw invalid(
                        indexContext + " (card '" + definition.id() + "').id",
                        "duplicate id '" + definition.id() + "'");
            }
        }
        return new CardCatalog(dataVersion, cards);
    }

    /**
     * Parses one card in isolation so callers can aggregate failures instead of stopping at the
     * first invalid card. {@code indexContext} prefixes every reported JSON path.
     */
    public CardDefinition parseCard(JsonNode cardNode, String indexContext) {
        if (cardNode == null || !cardNode.isObject()) {
            throw invalid(indexContext, "card must be an object");
        }

        String id = requiredText(cardNode, "id", indexContext);
        String cardContext = indexContext + " (card '" + id + "')";
        rejectUnknownFields(cardNode, ALLOWED_CARD_FIELDS, cardContext);
        return new CardDefinition(
                id,
                optionalText(cardNode, "cardName", cardContext),
                optionalText(cardNode, "favoredCharacter", cardContext),
                requiredNonNegativeInt(cardNode, "cost", cardContext),
                JsonValues.optionalBoolean(cardNode, "xCost", cardContext),
                parseCardType(cardNode, cardContext),
                parseTargetingMode(cardNode, cardContext),
                parseTags(cardNode, cardContext),
                parseModifiers(cardNode, cardContext),
                effectParser.parseEffects(cardNode, cardContext),
                parseRarity(cardNode, cardContext));
    }

    /**
     * How rare the card is, defaulting to Common when the data does not say.
     *
     * <p>An unknown rarity is not worth refusing a whole catalog over -- it costs the card
     * nothing but its place in a random pool -- so it reads as Common rather than throwing.
     */
    private static CardRarity parseRarity(JsonNode cardNode, String context) {
        String raw = optionalText(cardNode, "rarity", context);
        if (raw == null || raw.isBlank()) {
            return CardRarity.COMMON;
        }
        return CardRarity.fromJson(raw).orElse(CardRarity.COMMON);
    }

    private static CardType parseCardType(JsonNode cardNode, String context) {
        JsonNode type = cardNode.get("type");
        if (type == null || type.isNull()) {
            return CardType.NONE;
        }
        if (!type.isTextual()) {
            throw invalid(context + ".type", "must be a string");
        }
        String value = type.textValue();
        return CardType.fromJson(value)
                .orElseThrow(() -> invalid(context + ".type", "unsupported value '" + value + "'"));
    }

    private static TargetingMode parseTargetingMode(JsonNode cardNode, String context) {
        String value = requiredText(cardNode, "targetingMode", context);
        return TargetingMode.fromJson(value).orElseThrow(() -> invalid(
                context + ".targetingMode", "unsupported value '" + value + "'"));
    }

    private static List<CardModifier> parseModifiers(JsonNode cardNode, String context) {
        JsonNode modifiersNode = cardNode.get("modifiers");
        if (modifiersNode == null || modifiersNode.isNull()) {
            return List.of();
        }
        if (!modifiersNode.isArray()) {
            throw invalid(context + ".modifiers", "must be an array");
        }
        List<CardModifier> modifiers = new java.util.ArrayList<>(modifiersNode.size());
        for (int index = 0; index < modifiersNode.size(); index++) {
            JsonNode node = modifiersNode.get(index);
            String modifierContext = context + ".modifiers[" + index + "]";
            if (!node.isObject()) {
                throw invalid(modifierContext, "modifier must be an object");
            }
            rejectUnknownFields(node, ALLOWED_MODIFIER_FIELDS, modifierContext);
            modifiers.add(new CardModifier(
                    parseEnum(StatType.class, requiredText(node, "type", modifierContext),
                            modifierContext + ".type"),
                    parseEnum(ModifierKind.class, requiredText(node, "kind", modifierContext),
                            modifierContext + ".kind"),
                    JsonValues.requiredInt(node, "value", modifierContext),
                    optionalText(node, "info", modifierContext)));
        }
        return List.copyOf(modifiers);
    }

    /**
     * Unity writes these two vocabularies as its enum names, which the backend mirrors constant
     * for constant, so the match is on the constant itself rather than on a hand-kept table. Only
     * the spelling differs: Unity runs the words together where Java separates them.
     */
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String path) {
        String wanted = value.replace("_", "");
        for (E constant : type.getEnumConstants()) {
            if (constant.name().replace("_", "").equalsIgnoreCase(wanted)) {
                return constant;
            }
        }
        throw invalid(path, "unsupported value '" + value + "'");
    }

    private static Set<CardTag> parseTags(JsonNode cardNode, String context) {
        List<String> raw = optionalTextArray(cardNode, "tags", context);
        Set<CardTag> tags = new LinkedHashSet<>();
        for (int index = 0; index < raw.size(); index++) {
            String value = raw.get(index);
            int position = index;
            tags.add(CardTag.fromJson(value).orElseThrow(() -> invalid(
                    context + ".tags[" + position + "]", "unsupported value '" + value + "'")));
        }
        return Set.copyOf(tags);
    }
}
