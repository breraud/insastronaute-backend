package fr.insastronaute.webapi.sts.combat.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Compatibility matrix between the versioned Unity combat data and the authoritative engine.
 *
 * <p>This is the progress meter for the whole port: it pins the exact set of cards and enemies
 * the backend can serve today, over data copied verbatim from Unity. Making a mechanic work
 * shrinks an expectation below, in the same commit, so a gain is always deliberate and a
 * regression is always visible.
 *
 * <p>An id in {@code loadableCardIds} only means the definition builds. It is not a fidelity
 * claim: that is what the per-mechanic differential tests are for.
 */
class GameDataCompatibilityTest {

    private static final String DATA_VERSION = "unity-audit-2026-08";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Cards whose definition cannot be built at all, by the family that blocks them. */
    @Test
    void reportsEveryIncompatibilityOfTheDataVersionInASinglePass() throws Exception {
        GameDataCompatibilityReport report = scan();
        publish(report);

        assertThat(report.dataVersion()).isEqualTo(DATA_VERSION);
        assertThat(report.blockedCardIds())
                .describedAs("%s", report.describe())
                .isEmpty();
        assertThat(report.blockedEnemyIds()).isEmpty();
    }

    @Test
    void servesEveryCardOfTheSelectionInventory() throws Exception {
        GameDataCompatibilityReport report = scan();

        assertThat(report.issues(GameDataIssue.Scope.CARD))
                .describedAs("%s", report.describe())
                .isEmpty();
        assertThat(report.loadableCardIds()).hasSize(32);
    }

    @Test
    void keepsTheCardsOfTheSelectionInventoryThatAlreadyWorkOutOfTheIssueList() throws Exception {
        GameDataCompatibilityReport report = scan();

        assertThat(report.issuesFor("maquette")).isEmpty();
        assertThat(report.issuesFor("contrepoids")).isEmpty();
        assertThat(report.issuesFor("pieces_de_rechange")).isEmpty();
        assertThat(report.issuesFor("remise_en_etat")).isEmpty();
        assertThat(report.issuesFor("dispersion_atmospherique")).isEmpty();
        assertThat(report.issuesFor("rehabilitation")).isEmpty();
        assertThat(report.issuesFor("perf_e")).isEmpty();
        assertThat(report.issuesFor("raffinage"))
                .describedAs("its All source, Skill filter and -1 amount all resolve now")
                .isEmpty();
        assertThat(report.issuesFor("maille_parfaite"))
                .describedAs("consuming its Atoms for armour resolves now")
                .isEmpty();
        assertThat(report.issuesFor("reaction_chimique"))
                .describedAs("transforming cards resolves now")
                .isEmpty();
        assertThat(report.issuesFor("espace_vectoriel_norme")).isEmpty();
        assertThat(report.issuesFor("convertisseur_analogique")).isEmpty();
        assertThat(report.issuesFor("accelerateur_de_particules"))
                .describedAs("enchanting cards resolves now")
                .isEmpty();
        assertThat(report.issuesFor("resserrage_de_vis")).isEmpty();
        assertThat(report.issuesFor("redoubler")).isEmpty();
        assertThat(report.issuesFor("capteur_de_mouvement"))
                .describedAs("conditional effects load now")
                .isEmpty();
        assertThat(report.issuesFor("galanterie"))
                .describedAs("effects that ignore resistance load now")
                .isEmpty();
        assertThat(report.issuesFor("construction"))
                .describedAs("cards that scale through modifiers load now")
                .isEmpty();
        assertThat(report.issuesFor("fission")).isEmpty();
        assertThat(report.issuesFor("combustion"))
                .describedAs("a card costing whatever the caster holds loads now")
                .isEmpty();
        assertThat(report.issuesFor("fusion_nucleaire"))
                .describedAs("merging cards resolves now")
                .isEmpty();
        assertThat(report.issuesFor("amalgame")).isEmpty();
        assertThat(report.issuesFor("soudure")).isEmpty();
    }

    @Test
    void readsEveryEnemyMechanicOfTheFixture() throws Exception {
        GameDataCompatibilityReport report = scan();

        assertThat(report.issuesFor("Cultist")).isEmpty();
        assertThat(report.issuesFor("Aegis_Drone"))
                .describedAs("its starting status and its opening move are read now")
                .isEmpty();
    }

    @Test
    void reportsThatTheServerAnswersTheSelectionAnEnemyForces() throws Exception {
        GameDataCompatibilityReport report = scan();

        assertThat(report.issuesFor("Devourer"))
                .extracting(GameDataIssue::problem)
                .contains("the server picks the cards for this CardSelection,"
                        + " where the game asks the player");
    }

    @Test
    void reportsAnEffectThatCreatesACardTheCatalogCannotResolve() throws Exception {
        GameDataCompatibilityReport report = new GameDataCompatibilityScanner().scan(
                withoutCard(fixture("cards.json"), "debris_cosmique"), fixture("enemies.json"));

        assertThat(report.issuesFor("Devourer"))
                .extracting(GameDataIssue::problem)
                .contains("creates card 'Débris cosmique' which is not loadable");
    }

    @Test
    void resolvesACardReferencedByItsAuthoredNameRatherThanItsId() throws Exception {
        GameDataCompatibilityReport report = scan();

        assertThat(report.issuesFor("Devourer"))
                .describedAs("Devourer creates 'Débris cosmique', whose id is debris_cosmique")
                .extracting(GameDataIssue::problem)
                .noneSatisfy(problem -> assertThat(problem).contains("not loadable"));
    }

    @Test
    void producesAStableReportAcrossRuns() throws Exception {
        assertThat(scan().describe()).isEqualTo(scan().describe());
    }

    /** Keeps the readable matrix as a build artifact instead of burying it in assertion output. */
    private static void publish(GameDataCompatibilityReport report) throws Exception {
        Path target = Path.of("build", "reports", "sts", "compatibility-" + DATA_VERSION + ".txt");
        Files.createDirectories(target.getParent());
        Files.writeString(target, report.describe());
    }

    private static GameDataCompatibilityReport scan() throws Exception {
        return new GameDataCompatibilityScanner().scan(fixture("cards.json"), fixture("enemies.json"));
    }

    private static JsonNode withoutCard(JsonNode cardsRoot, String cardId) {
        ArrayNode cards = (ArrayNode) cardsRoot.get("cards");
        for (int index = 0; index < cards.size(); index++) {
            if (cardId.equals(cards.get(index).path("id").asText())) {
                cards.remove(index);
                return cardsRoot;
            }
        }
        throw new IllegalStateException("fixture has no card " + cardId);
    }

    private static JsonNode fixture(String name) throws Exception {
        String path = "/sts/" + DATA_VERSION + "/" + name;
        try (InputStream stream = GameDataCompatibilityTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("missing fixture " + path);
            }
            return OBJECT_MAPPER.readTree(stream);
        }
    }
}
