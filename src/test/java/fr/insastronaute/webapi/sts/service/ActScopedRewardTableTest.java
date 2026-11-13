package fr.insastronaute.webapi.sts.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Choisir la table de récompenses d'un acte parmi celles que les données déclinent.
 *
 * <p>La règle est celle du « dernier acte déjà atteint » : à l'Acte 3, une table qui ne connaît
 * que les actes 1 et 2 répond avec celle de l'acte 2. C'est ce qui permet d'écrire un acte quand
 * l'équilibrage change, et de laisser les suivants en hériter sans les recopier.
 *
 * <p>Le repli ne remonte jamais vers un acte plus tardif : à l'Acte 1, une table écrite pour
 * l'acte 2 ne s'applique pas, et c'est la table non déclinée qui répond. Sans cette borne, un
 * premier acte tirerait les raretés pensées pour la fin de partie.
 *
 * <p>Les numéros s'écrivent comme le joueur les voit — {@code "1"} est l'Acte 1 — alors que
 * l'index interne d'une run commence à zéro.
 */
class ActScopedRewardTableTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String BY_ACT = """
            {"rarityWeights":{"marker":"base"},
             "byAct":{
               "1":{"rarityWeights":{"marker":"act1"}},
               "3":{"rarityWeights":{"marker":"act3"}}}}
            """;

    @Test
    void anActWithItsOwnTableReadsIt() {
        assertThat(markerFor(BY_ACT, 3)).isEqualTo("act3");
    }

    @Test
    void anActWithoutOneReadsTheLatestTableItHasAlreadyReached() {
        assertThat(markerFor(BY_ACT, 2)).isEqualTo("act1");
        assertThat(markerFor(BY_ACT, 4)).isEqualTo("act3");
    }

    /** Une déclinaison écrite pour plus tard ne s'applique pas à un acte plus tôt. */
    @Test
    void anActBeforeEveryAuthoredOneFallsBackToTheUndeclinedTable() {
        assertThat(markerFor("""
                {"rarityWeights":{"marker":"base"},
                 "byAct":{"2":{"rarityWeights":{"marker":"act2"}}}}
                """, 1)).isEqualTo("base");
    }

    /** Une table sans aucune déclinaison répond comme avant, pour tous les actes. */
    @Test
    void aTableWithoutActsIsReadUnchanged() {
        String flat = """
                {"rarityWeights":{"marker":"base"}}
                """;

        assertThat(markerFor(flat, 1)).isEqualTo("base");
        assertThat(markerFor(flat, 9)).isEqualTo("base");
    }

    /** Les clés se lisent aussi écrites « act2 » ou « Acte 2 » : elles sont posées à la main. */
    @Test
    void anActKeyIsReadHoweverItIsSpelled() {
        assertThat(markerFor("""
                {"rarityWeights":{"marker":"base"},
                 "acts":{"act2":{"rarityWeights":{"marker":"act2"}},
                         "Acte 3":{"rarityWeights":{"marker":"act3"}}}}
                """, 2)).isEqualTo("act2");
    }

    /** La déclinaison s'écrit aussi comme une liste, chaque entrée nommant son acte. */
    @Test
    void anActListIsReadLikeAnActMap() {
        String list = """
                {"rarityWeights":{"marker":"base"},
                 "byAct":[{"act":1,"rarityWeights":{"marker":"act1"}},
                          {"act":3,"rarityWeights":{"marker":"act3"}}]}
                """;

        assertThat(markerFor(list, 2)).isEqualTo("act1");
        assertThat(markerFor(list, 3)).isEqualTo("act3");
    }

    /**
     * La table de l'acte passe avant la table non déclinée, jamais l'inverse.
     *
     * <p>C'est tout l'intérêt du réglage : ce que l'acte dit remplace ce que la section dit, et
     * une section qui garde ses anciens poids ne doit pas les réimposer.
     */
    @Test
    void theActTableIsConsultedBeforeTheUndeclinedOne() {
        assertThat(scopesFor(BY_ACT, 3))
                .extracting(scope -> scope.path("rarityWeights").path("marker").asText())
                .containsExactly("act3", "base");
    }

    /** Le premier scope est celui de l'acte ; le marqueur lu vient de lui. */
    private String markerFor(String json, int act) {
        for (JsonNode scope : scopesFor(json, act)) {
            JsonNode marker = scope.path("rarityWeights").path("marker");
            if (marker.isTextual()) {
                return marker.asText();
            }
        }
        throw new AssertionError("no scope carried a marker");
    }

    private java.util.List<JsonNode> scopesFor(String json, int act) {
        try {
            return StsRunService.actScopes(mapper.readTree(json), act);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
