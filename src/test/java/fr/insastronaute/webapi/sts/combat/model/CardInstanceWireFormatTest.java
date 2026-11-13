package fr.insastronaute.webapi.sts.combat.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The vocabulary a card instance is written in.
 *
 * <p>The client parses what the snapshot sends against its own enums, and those are spelled the
 * way the authored card data spells them: {@code Attaque}, {@code Enemy}, {@code FollowUp}. Java's
 * constant names are not those, so every per-instance override the engine sent -- a merged name's
 * targeting, a conjured copy's family, an Exhaust pinned on mid-combat -- arrived as a word the
 * client could not read and was silently dropped.
 *
 * <p>The other direction has to keep working too: combats saved before this was true hold the
 * constant names, and they still have to load.
 */
class CardInstanceWireFormatTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void writesTheSpellingTheClientReads() throws Exception {
        CardInstance instance = new CardInstance(
                "inst", "carte", List.of(), "Nom fusionné", TargetingMode.SINGLE_ENEMY,
                List.of(CardTag.FOLLOW_UP, CardTag.EXHAUST), List.of(), List.of(),
                CardType.ATTACK, 2);

        JsonNode json = mapper.valueToTree(instance);

        assertThat(json.path("cardType").asText()).isEqualTo("Attaque");
        assertThat(json.path("targetingMode").asText()).isEqualTo("Enemy");
        assertThat(json.path("tags").get(0).asText()).isEqualTo("FollowUp");
        assertThat(json.path("tags").get(1).asText()).isEqualTo("Exhaust");
        assertThat(json.path("displayName").asText()).isEqualTo("Nom fusionné");
        assertThat(json.path("cost").asInt()).isEqualTo(2);
    }

    @Test
    void readsBackWhatItWrote() throws Exception {
        CardInstance instance = new CardInstance(
                "inst", "carte", List.of(), null, TargetingMode.ALL_ENEMIES,
                List.of(CardTag.ETHEREAL), List.of(), List.of(), CardType.SKILL, 1);

        CardInstance round = mapper.readValue(mapper.writeValueAsString(instance), CardInstance.class);

        assertThat(round).isEqualTo(instance);
    }

    /** A combat saved before the change holds the constant names, and still has to load. */
    @Test
    void stillReadsTheConstantNamesAnOlderCombatWasSavedWith() throws Exception {
        String saved = """
                {"instanceId":"inst","definitionId":"carte","enchantments":[],
                 "displayName":null,"targetingMode":"SINGLE_ENEMY","tags":["FOLLOW_UP","EXHAUST"],
                 "modifiers":[],"addedEffects":[],"cardType":"ATTACK","cost":2}
                """;

        CardInstance instance = mapper.readValue(saved, CardInstance.class);

        assertThat(instance.targetingMode()).isEqualTo(TargetingMode.SINGLE_ENEMY);
        assertThat(instance.tags()).containsExactly(CardTag.FOLLOW_UP, CardTag.EXHAUST);
        assertThat(instance.cardType()).isEqualTo(CardType.ATTACK);
    }

    /** A plain card says nothing about any of it, so an ordinary combat is no heavier on the wire. */
    @Test
    void aPlainCardCarriesNoOverrides() throws Exception {
        JsonNode json = mapper.valueToTree(new CardInstance("inst", "carte"));

        assertThat(json.path("cardType").isNull()).isTrue();
        assertThat(json.path("cost").isNull()).isTrue();
        assertThat(json.path("displayName").isNull()).isTrue();
    }
}
