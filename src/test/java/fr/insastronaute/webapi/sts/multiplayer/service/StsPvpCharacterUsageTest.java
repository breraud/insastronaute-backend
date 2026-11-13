package fr.insastronaute.webapi.sts.multiplayer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Qui joue quel personnage, et avec quel succès.
 *
 * <p>La statistique relit les équipes que chaque bataille garde déjà. Ce qui se vérifie ici est
 * ce qu'elle doit compter et, surtout, ce qu'elle ne doit pas : les sièges que le serveur joue, et
 * les parties nulles dans le taux de victoire.
 */
class StsPvpCharacterUsageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void countsEachPlayersCharacterAndWhetherItWon() {
        StsPvpCharacterUsage.Result result = StsPvpCharacterUsage.compute(List.of(
                duel("EP", "MECA", 0),
                duel("EP", "ITI", 0),
                duel("MECA", "EP", 0)));

        assertThat(result.battles()).isEqualTo(3);
        assertThat(result.picks()).isEqualTo(6);

        StsPvpCharacterUsage.Row ep = row(result, "EP");
        assertThat(ep.picks()).isEqualTo(3);
        assertThat(ep.wins()).isEqualTo(2);
        assertThat(ep.losses()).isEqualTo(1);
        assertThat(ep.share()).isCloseTo(0.5d, within(1e-9));
        assertThat(ep.winRate()).isCloseTo(2d / 3d, within(1e-9));
    }

    @Test
    void listsTheMostPlayedFirst() {
        StsPvpCharacterUsage.Result result = StsPvpCharacterUsage.compute(List.of(
                duel("ITI", "EP", 0),
                duel("EP", "MECA", 1),
                duel("EP", "EP", 0)));

        assertThat(result.characters()).extracting(StsPvpCharacterUsage.Row::character)
                .startsWith("EP");
    }

    /**
     * Un renfort emprunte le personnage et le deck d'un coéquipier : le compter ferait valoir
     * double le choix de ce coéquipier. Un boss n'a pas de personnage du tout.
     */
    @Test
    void ignoresSeatsTheServerPlays() {
        ArrayNode teams = objectMapper.createArrayNode();
        ArrayNode team0 = teams.addArray();
        team0.add(human("EP"));
        team0.add(human("EP").put("controller", "AI").put("deckOwnerUserId", "00000000-0000-0000-0000-000000000001"));
        ArrayNode team1 = teams.addArray();
        team1.addObject().put("controller", "AI").put("bossEnemyId", "Ironclad");

        StsPvpCharacterUsage.Result result = StsPvpCharacterUsage.compute(List.of(
                new StsPvpCharacterUsage.Battle(teams, 0)));

        assertThat(result.picks()).isEqualTo(1);
        assertThat(result.characters()).singleElement()
                .satisfies(row -> assertThat(row.character()).isEqualTo("EP"));
    }

    /**
     * Un nul ne dit rien de la force d'un personnage : le compter comme une défaite pénaliserait
     * celui qu'on joue dans les parties serrées.
     */
    @Test
    void keepsDrawsOutOfTheWinRate() {
        StsPvpCharacterUsage.Result result = StsPvpCharacterUsage.compute(List.of(
                duel("EP", "MECA", 0),
                duel("EP", "MECA", null)));

        StsPvpCharacterUsage.Row ep = row(result, "EP");
        assertThat(ep.draws()).isEqualTo(1);
        assertThat(ep.winRate()).isEqualTo(1d);
    }

    @Test
    void aCharacterWithOnlyDrawsHasNoWinRate() {
        StsPvpCharacterUsage.Result result = StsPvpCharacterUsage.compute(List.of(duel("EP", "MECA", null)));

        assertThat(row(result, "EP").winRate()).isNull();
    }

    /** Les batailles d'avant les modes n'ont aucun marqueur de pilote : ce sont bien des joueurs. */
    @Test
    void readsBattlesFromBeforeTheModesExisted() {
        ArrayNode teams = objectMapper.createArrayNode();
        teams.addArray().addObject().put("userId", "a").put("selectedCharacter", "EP");
        teams.addArray().addObject().put("userId", "b").put("selectedCharacter", "MECA");

        StsPvpCharacterUsage.Result result = StsPvpCharacterUsage.compute(List.of(
                new StsPvpCharacterUsage.Battle(teams, 1)));

        assertThat(result.picks()).isEqualTo(2);
        assertThat(row(result, "MECA").wins()).isEqualTo(1);
    }

    @Test
    void aPlayerWithoutACharacterPlaysTheDefaultOne() {
        ArrayNode teams = objectMapper.createArrayNode();
        teams.addArray().addObject().put("userId", "a");
        teams.addArray().addObject().put("userId", "b").put("selectedCharacter", "  ");

        StsPvpCharacterUsage.Result result = StsPvpCharacterUsage.compute(List.of(
                new StsPvpCharacterUsage.Battle(teams, 0)));

        assertThat(row(result, StsPvpCharacterUsage.DEFAULT_CHARACTER).picks()).isEqualTo(2);
    }

    @Test
    void nothingToCountIsAnEmptyResultNotAnError() {
        StsPvpCharacterUsage.Result result = StsPvpCharacterUsage.compute(List.of());

        assertThat(result.battles()).isZero();
        assertThat(result.picks()).isZero();
        assertThat(result.characters()).isEmpty();
    }

    @Test
    void twoVersusTwoCountsBothPlayersOfEachTeam() {
        ArrayNode teams = objectMapper.createArrayNode();
        ArrayNode team0 = teams.addArray();
        team0.add(human("EP"));
        team0.add(human("MECA"));
        ArrayNode team1 = teams.addArray();
        team1.add(human("ITI"));
        team1.add(human("EP"));

        StsPvpCharacterUsage.Result result = StsPvpCharacterUsage.compute(List.of(
                new StsPvpCharacterUsage.Battle(teams, 1)));

        assertThat(result.picks()).isEqualTo(4);
        StsPvpCharacterUsage.Row ep = row(result, "EP");
        assertThat(ep.picks()).isEqualTo(2);
        assertThat(ep.wins()).isEqualTo(1);
        assertThat(ep.losses()).isEqualTo(1);
    }

    private StsPvpCharacterUsage.Battle duel(String first, String second, Integer winnerTeam) {
        ArrayNode teams = objectMapper.createArrayNode();
        teams.addArray().add(human(first));
        teams.addArray().add(human(second));
        return new StsPvpCharacterUsage.Battle(teams, winnerTeam);
    }

    private ObjectNode human(String character) {
        return objectMapper.createObjectNode()
                .put("controller", "HUMAN")
                .put("selectedCharacter", character);
    }

    private static StsPvpCharacterUsage.Row row(StsPvpCharacterUsage.Result result, String character) {
        return result.characters().stream()
                .filter(row -> row.character().equals(character))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no row for " + character));
    }
}
