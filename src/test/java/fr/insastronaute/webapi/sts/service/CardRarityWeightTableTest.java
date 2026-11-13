package fr.insastronaute.webapi.sts.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Reading the rarity weights a reward table actually authors.
 *
 * <p>The card table splits its weights by kind of node -- {@code rarityWeights: { normal: {...},
 * boss: {...} }} -- and the reader only understood a flat table. It found nothing, and finding
 * nothing does not fail: it falls back to weighing every card the same. Over the authored card
 * set that inverted the whole curve, because a character has some thirty epics and legendaries
 * available and only seven commons. Commons appeared in one reward in five, legendaries in two.
 */
class CardRarityWeightTableTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String SPLIT = """
            {"normal":{"Common":100,"Uncommon":50,"Rare":25,"Epic":10,"Legendary":5,"Special":0},
             "boss":{"Common":0,"Uncommon":0,"Rare":0,"Epic":0,"Legendary":50,"Special":0}}
            """;

    private static final String FLAT =
            """
            {"Common":60,"Uncommon":25,"Rare":15,"Boss":0,"Base":0}
            """;

    @Test
    void anOrdinaryFightReadsTheNormalTable() {
        Map<String, Integer> weights = weightsFor(SPLIT, "Combat");

        assertThat(weights).containsEntry("common", 100).containsEntry("legendary", 5);
    }

    /** Commons outweigh legendaries twentyfold; uniform weighting is what inverted that. */
    @Test
    void commonsOutweighLegendaries() {
        Map<String, Integer> weights = weightsFor(SPLIT, "Combat");

        assertThat(weights.get("common")).isGreaterThan(weights.get("legendary") * 10);
    }

    @Test
    void aBossReadsItsOwnTable() {
        Map<String, Integer> weights = weightsFor(SPLIT, "Boss");

        assertThat(weights).containsEntry("legendary", 50).containsEntry("common", 0);
    }

    /** An elite has no table of its own, so it draws on the ordinary one. */
    @Test
    void anEliteFallsBackToTheNormalTable() {
        assertThat(weightsFor(SPLIT, "Elite")).isEqualTo(weightsFor(SPLIT, "Combat"));
    }

    @Test
    void anEventAlsoFallsBackToTheNormalTable() {
        assertThat(weightsFor(SPLIT, "Event")).containsEntry("common", 100);
    }

    /**
     * Whatever it reads, it must not come back empty for this table.
     *
     * <p>Empty is the dangerous answer: it does not fail, it means "weigh everything the same".
     */
    @Test
    void neverFallsBackToWeighingEveryCardTheSame() {
        for (String nodeType : new String[] {"Combat", "Elite", "Boss", "Event", "", null}) {
            assertThat(weightsFor(SPLIT, nodeType))
                    .as("weights for node type '" + nodeType + "'")
                    .isNotEmpty();
        }
    }

    /** A table that is not split by node kind is still read exactly as written. */
    @Test
    void aFlatTableIsReadUnchanged() {
        assertThat(weightsFor(FLAT, "Elite"))
                .containsEntry("common", 60)
                .containsEntry("rare", 15);
    }

    /**
     * A rarity that happens to be spelled "normal" is a rarity, not a section.
     *
     * <p>The two are told apart by shape: a section's value is a table, a rarity's value is a
     * number.
     */
    @Test
    void aRarityCalledNormalIsNotMistakenForASection() {
        assertThat(weightsFor("""
                {"normal":40,"Rare":10}
                """, "Combat")).containsEntry("normal", 40).containsEntry("rare", 10);
    }

    private Map<String, Integer> weightsFor(String json, String nodeType) {
        try {
            JsonNode container = mapper.readTree(json);
            return StsRunService.parseWeightMap(
                    StsRunService.weightsForNodeKind(container, nodeType));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
