package fr.insastronaute.webapi.sts.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The pool an event asks its card reward to be drawn from.
 *
 * <p>Read straight from the payload Unity exports, ordinals and all, because that is what the
 * server actually receives: an event written to hand out another character's cards says so with
 * {@code specificFavoredCharacter: 1}, and nothing in the JSON says that means MECA.
 */
class EventCardRewardProfileTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String MECA_ONLY = """
            {"useSelectedCharacterBias":false,"useSpecificFavoredCharacter":true,
             "specificFavoredCharacter":1,"allowCreatedCards":false,
             "allowUnobtainableCards":false,"useExactRarity":false,"exactRarity":0,
             "useExactCost":false,"exactCost":0,"useCostRange":false,"minimumCost":0,
             "maximumCost":0,"useExactType":false,"exactType":0,
             "overrideDefaultWeights":false,"rarityWeights":[],"requiredTags":[],
             "excludedTags":[],"choiceCount":3}
            """;

    @Test
    void readsTheCharacterOutOfItsOrdinal() {
        EventCardRewardProfile profile = profile(MECA_ONLY);

        assertThat(profile.accepts(card("MECA", "Common", 1, "Attaque"), "ITI"))
                .as("a MECA card, offered to an ITI run, which is the whole point of the event")
                .isTrue();
    }

    @Test
    void refusesEveryOtherCharacter() {
        EventCardRewardProfile profile = profile(MECA_ONLY);

        assertThat(profile.accepts(card("ITI", "Common", 1, "Attaque"), "ITI")).isFalse();
        assertThat(profile.accepts(card("", "Common", 1, "Attaque"), "ITI"))
                .as("not even a neutral card, once a character is named")
                .isFalse();
    }

    /** Without a profile named character, the run's own bias applies as it always did. */
    @Test
    void fallsBackToTheRunsCharacterWhenNoneIsNamed() {
        EventCardRewardProfile profile = profile("""
                {"useSelectedCharacterBias":true,"useSpecificFavoredCharacter":false,
                 "specificFavoredCharacter":0,"choiceCount":3}
                """);

        assertThat(profile.accepts(card("ITI", "Common", 1, "Attaque"), "ITI")).isTrue();
        assertThat(profile.accepts(card("MECA", "Common", 1, "Attaque"), "ITI")).isFalse();
        assertThat(profile.accepts(card("Aucun", "Common", 1, "Attaque"), "ITI"))
                .as("a neutral card belongs to every run")
                .isTrue();
    }

    @Test
    void narrowsByRarityCostAndType() {
        EventCardRewardProfile profile = profile("""
                {"useSelectedCharacterBias":false,"useExactRarity":true,"exactRarity":2,
                 "useCostRange":true,"minimumCost":1,"maximumCost":2,
                 "useExactType":true,"exactType":0,"choiceCount":3}
                """);

        assertThat(profile.accepts(card("ITI", "Rare", 2, "Attaque"), "ITI")).isTrue();
        assertThat(profile.accepts(card("ITI", "Common", 2, "Attaque"), "ITI")).isFalse();
        assertThat(profile.accepts(card("ITI", "Rare", 3, "Attaque"), "ITI")).isFalse();
        assertThat(profile.accepts(card("ITI", "Rare", 2, "Compétence"), "ITI")).isFalse();
    }

    /** Created and unobtainable cards stay out unless the event asks for them by name. */
    @Test
    void keepsTheCardsNobodyIsMeantToBeOfferedOut() {
        EventCardRewardProfile profile = profile(MECA_ONLY);
        JsonNode created = mapper.valueToTree(java.util.Map.of(
                "favoredCharacter", "MECA", "rarity", "Common", "cost", 1, "type", "Attaque",
                "tags", List.of("Created")));

        assertThat(profile.accepts(created, "ITI")).isFalse();
    }

    @Test
    void weighsTheRaritiesItNames() {
        EventCardRewardProfile profile = profile("""
                {"useSelectedCharacterBias":false,"overrideDefaultWeights":true,
                 "rarityWeights":[{"rarity":2,"weight":40}],"choiceCount":3}
                """);

        assertThat(profile.weight(card("ITI", "Rare", 1, "Attaque"), 7)).isEqualTo(40);
        assertThat(profile.weight(card("ITI", "Common", 1, "Attaque"), 7))
                .as("overriding the defaults means an unnamed rarity weighs nothing")
                .isZero();
    }

    /** Without the override, an unnamed rarity keeps the node's own weight. */
    @Test
    void leavesUnnamedRaritiesAloneWhenNotOverriding() {
        EventCardRewardProfile profile = profile("""
                {"useSelectedCharacterBias":false,"overrideDefaultWeights":false,
                 "rarityWeights":[{"rarity":2,"weight":40}],"choiceCount":3}
                """);

        assertThat(profile.weight(card("ITI", "Common", 1, "Attaque"), 7)).isEqualTo(7);
    }

    @Test
    void takesItsChoiceCountFromTheEventAndFallsBackWhenItSaysNothing() {
        assertThat(profile(MECA_ONLY).choiceCount(9)).isEqualTo(3);
        assertThat(profile("""
                {"useSelectedCharacterBias":false,"choiceCount":0}
                """).choiceCount(9)).isEqualTo(9);
    }

    /** An entry that authors nothing asks for the ordinary reward, not an empty pool. */
    @Test
    void anEntryWithoutAProfileAsksForNothingInParticular() {
        JsonNode entry = mapper.valueToTree(java.util.Map.of("type", "CardReward"));

        assertThat(EventCardRewardProfile.allOf(entry)).isEmpty();
    }

    /** The list wins over the single legacy field, which both appear in the exported data. */
    @Test
    void prefersTheListOverTheSingleLegacyProfile() throws Exception {
        JsonNode entry = mapper.readTree("""
                {"type":"CardReward",
                 "cardRewardProfile":{"useSpecificFavoredCharacter":true,
                                      "specificFavoredCharacter":3,"choiceCount":1},
                 "cardRewardProfiles":[{"useSpecificFavoredCharacter":true,
                                        "specificFavoredCharacter":1,"choiceCount":3}]}
                """);

        List<EventCardRewardProfile> profiles = EventCardRewardProfile.allOf(entry);

        assertThat(profiles).hasSize(1);
        assertThat(profiles.getFirst().accepts(card("MECA", "Common", 1, "Attaque"), "ITI"))
                .as("the list's MECA, not the legacy field's ITI")
                .isTrue();
    }

    private EventCardRewardProfile profile(String json) {
        try {
            return EventCardRewardProfile.of(mapper.readTree(json));
        } catch (Exception cannotHappen) {
            throw new IllegalStateException(cannotHappen);
        }
    }

    private JsonNode card(String favored, String rarity, int cost, String type) {
        return mapper.valueToTree(java.util.Map.of(
                "favoredCharacter", favored, "rarity", rarity, "cost", cost, "type", type,
                "tags", List.of()));
    }
}
