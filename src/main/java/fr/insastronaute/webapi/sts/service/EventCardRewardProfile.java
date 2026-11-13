package fr.insastronaute.webapi.sts.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The pool an event's card reward is drawn from, as the event data authors it.
 *
 * <p>Events can ask for something other than the usual reward -- a card of another character, a
 * rarity, a cost band -- and Unity has carried that as a {@code CardRewardProfile} on the option
 * entry all along. The server generated every event reward from the run's own character and the
 * node's default weights instead, so an event written to hand out a MECA card handed out whatever
 * an ordinary reward would have.
 *
 * <p>The numbers arrive as enum <em>ordinals</em>: Unity's {@code JsonUtility} writes an enum as
 * its position in the declaration, so {@code specificFavoredCharacter: 4} is CFI and nothing in
 * the payload says so. The orders below therefore mirror the Unity enums exactly, and are the one
 * thing here that must be kept in step with them.
 */
public final class EventCardRewardProfile {

    /** Mirrors Unity's {@code SelectableCharacter} declaration order. */
    private static final List<String> CHARACTERS = List.of(
            "EP", "MECA", "GM", "ITI", "CFI", "MRIE", "GC", "AI", "PERF",
            "Aucun", "Impossible", "Starting");

    /** Mirrors Unity's {@code CardRarity} declaration order. */
    private static final List<String> RARITIES = List.of(
            "Common", "Uncommon", "Rare", "Epic", "Legendary", "Special");

    /** Mirrors Unity's {@code CardType} declaration order. */
    private static final List<String> TYPES = List.of("Attaque", "Compétence", "Pouvoir", "Rien");

    /** Mirrors Unity's {@code CardTag} declaration order. */
    private static final List<String> TAGS = List.of(
            "None", "Attack", "Skill", "Power", "Status", "Curse", "Created", "Unobtainable",
            "FollowUp", "Exhaust", "Retain", "Ethereal", "Innate", "Infinite", "Atom", "Molecule",
            "Norm", "Developer", "Automatic", "Unplayable", "SecretUnlock", "Secret");

    private final boolean useSelectedCharacterBias;
    private final String specificFavoredCharacter;
    private final boolean allowCreatedCards;
    private final boolean allowUnobtainableCards;
    private final String exactRarity;
    private final Integer exactCost;
    private final Integer minimumCost;
    private final Integer maximumCost;
    private final String exactType;
    private final boolean overrideDefaultWeights;
    private final Map<String, Integer> rarityWeights;
    private final List<String> requiredTags;
    private final List<String> excludedTags;
    private final int choiceCount;

    private EventCardRewardProfile(JsonNode node) {
        this.useSelectedCharacterBias = node.path("useSelectedCharacterBias").asBoolean(true);
        this.specificFavoredCharacter = node.path("useSpecificFavoredCharacter").asBoolean(false)
                ? name(CHARACTERS, node.path("specificFavoredCharacter"))
                : null;
        this.allowCreatedCards = node.path("allowCreatedCards").asBoolean(false);
        this.allowUnobtainableCards = node.path("allowUnobtainableCards").asBoolean(false);
        this.exactRarity = node.path("useExactRarity").asBoolean(false)
                ? name(RARITIES, node.path("exactRarity"))
                : null;
        this.exactCost = node.path("useExactCost").asBoolean(false)
                ? node.path("exactCost").asInt(0)
                : null;
        boolean range = node.path("useCostRange").asBoolean(false);
        this.minimumCost = range ? node.path("minimumCost").asInt(0) : null;
        this.maximumCost = range ? node.path("maximumCost").asInt(0) : null;
        this.exactType = node.path("useExactType").asBoolean(false)
                ? name(TYPES, node.path("exactType"))
                : null;
        this.overrideDefaultWeights = node.path("overrideDefaultWeights").asBoolean(false);

        Map<String, Integer> weights = new LinkedHashMap<>();
        for (JsonNode weight : node.path("rarityWeights")) {
            String rarity = name(RARITIES, weight.path("rarity"));
            if (rarity != null) {
                weights.put(rarity, Math.max(0, weight.path("weight").asInt(1)));
            }
        }
        this.rarityWeights = Map.copyOf(weights);
        this.requiredTags = tags(node.path("requiredTags"));
        this.excludedTags = tags(node.path("excludedTags"));
        this.choiceCount = Math.max(0, node.path("choiceCount").asInt(0));
    }

    /** @return the profile this entry authors, or null when it asks for the ordinary reward */
    public static EventCardRewardProfile of(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() || !node.isObject()
                ? null
                : new EventCardRewardProfile(node);
    }

    /**
     * Every profile an event entry carries, newest field first.
     *
     * <p>The entry holds both a single {@code cardRewardProfile} and a list; the list is what
     * Unity fills today and the single one is what it filled before, so the list wins and the
     * single one is the fallback. An entry with neither asks for one ordinary reward.
     */
    public static List<EventCardRewardProfile> allOf(JsonNode entry) {
        List<EventCardRewardProfile> profiles = new ArrayList<>();
        for (JsonNode node : entry.path("cardRewardProfiles")) {
            EventCardRewardProfile profile = of(node);
            if (profile != null) {
                profiles.add(profile);
            }
        }
        if (profiles.isEmpty()) {
            EventCardRewardProfile single = of(entry.path("cardRewardProfile"));
            if (single != null) {
                profiles.add(single);
            }
        }
        return profiles;
    }

    /** How many cards to offer, or the reward table's own count when the profile does not say. */
    public int choiceCount(int fallback) {
        return choiceCount > 0 ? choiceCount : fallback;
    }

    /**
     * Whether this card may be offered, given the run's own character.
     *
     * <p>Mirrors {@code CardRewardProfile.Matches}: the character gate first, then the markers,
     * then each authored narrowing in turn.
     */
    public boolean accepts(JsonNode card, String runCharacter) {
        String favored = card.path("favoredCharacter").asText("").trim();
        if ("Starting".equalsIgnoreCase(favored) || "Impossible".equalsIgnoreCase(favored)) {
            return false;
        }
        if (!allowCreatedCards && hasTag(card, "Created")) {
            return false;
        }
        if (!allowUnobtainableCards && hasTag(card, "Unobtainable")) {
            return false;
        }
        if (specificFavoredCharacter != null) {
            if (!specificFavoredCharacter.equalsIgnoreCase(favored)) {
                return false;
            }
        } else if (useSelectedCharacterBias
                && runCharacter != null
                && !runCharacter.isBlank()
                && !favored.isBlank()
                && !"Aucun".equalsIgnoreCase(favored)
                && !runCharacter.equalsIgnoreCase(favored)) {
            return false;
        }
        if (exactRarity != null && !exactRarity.equalsIgnoreCase(card.path("rarity").asText("").trim())) {
            return false;
        }
        int cost = card.path("cost").asInt(0);
        if (exactCost != null && cost != exactCost) {
            return false;
        }
        if (minimumCost != null && (cost < minimumCost || cost > maximumCost)) {
            return false;
        }
        if (exactType != null && !exactType.equalsIgnoreCase(card.path("type").asText("").trim())) {
            return false;
        }
        for (String tag : requiredTags) {
            if (!hasTag(card, tag)) {
                return false;
            }
        }
        for (String tag : excludedTags) {
            if (hasTag(card, tag)) {
                return false;
            }
        }
        return true;
    }

    /**
     * How heavily this card is weighted, or {@code fallback} when the profile leaves it alone.
     *
     * <p>A rarity the profile does not name weighs nothing when it overrides the defaults, and
     * keeps the node's own weight otherwise -- which is what lets an event say "only Rares" by
     * naming one rarity rather than by zeroing the other five.
     */
    public int weight(JsonNode card, int fallback) {
        if (rarityWeights.isEmpty()) {
            return fallback;
        }
        String rarity = card.path("rarity").asText("").trim();
        for (Map.Entry<String, Integer> entry : rarityWeights.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(rarity)) {
                return entry.getValue();
            }
        }
        return overrideDefaultWeights ? 0 : fallback;
    }

    private static boolean hasTag(JsonNode card, String tag) {
        for (JsonNode candidate : card.path("tags")) {
            if (tag.equalsIgnoreCase(candidate.asText(""))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reads a name from an ordinal, tolerating a payload that spells it out instead.
     *
     * <p>Unity writes ordinals, but the same data is also authored by hand and exported by other
     * tools, so a textual value is accepted rather than silently read as ordinal zero -- which is
     * "EP", "Common" and "Attaque", three wrong answers that all look like real ones.
     */
    private static String name(List<String> names, JsonNode value) {
        if (value.isTextual()) {
            String text = value.asText("").trim();
            return text.isEmpty() ? null : text;
        }
        if (!value.isIntegralNumber()) {
            return null;
        }
        int index = value.asInt(-1);
        return index >= 0 && index < names.size() ? names.get(index) : null;
    }

    private static List<String> tags(JsonNode node) {
        List<String> tags = new ArrayList<>();
        for (JsonNode tag : node) {
            String name = name(TAGS, tag);
            if (name != null && !name.isEmpty()) {
                tags.add(name);
            }
        }
        return List.copyOf(tags);
    }
}
