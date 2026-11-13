package fr.insastronaute.webapi.sts.multiplayer.combat;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpRaidEncounters.Encounter;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les rencontres de raid lues depuis {@code gameplay/pvp_raid_encounters.json}.
 *
 * <p>Ce qui compte ici est qu'un fichier mal écrit ne rende jamais un raid impossible à lancer :
 * au pire il retombe sur le boss par défaut, ou sur l'échelle de PV ordinaire.
 */
class StsPvpRaidEncountersTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsAListOfEnemyLists() throws Exception {
        StsPvpRaidEncounters encounters = StsPvpRaidEncounters.from(objectMapper.readTree(
                "{\"encounters\":[[\"Behemoth\"],[\"Aegis_Drone\",\"Astrum\"]]}"));

        assertThat(encounters.encounters()).containsExactly(
                new Encounter(List.of("Behemoth"), 1d),
                new Encounter(List.of("Aegis_Drone", "Astrum"), 1d));
    }

    @Test
    void alsoReadsTheBareListOfLists() throws Exception {
        StsPvpRaidEncounters encounters = StsPvpRaidEncounters.from(
                objectMapper.readTree("[[\"Behemoth\"],[\"Astrum\",\"Astrum\"]]"));

        assertThat(encounters.encounters()).extracting(Encounter::enemies)
                .containsExactly(List.of("Behemoth"), List.of("Astrum", "Astrum"));
    }

    /** Une rencontre écrite en objet règle sa propre échelle de points de vie. */
    @Test
    void anEncounterCanSetItsOwnHpMultiplier() throws Exception {
        StsPvpRaidEncounters encounters = StsPvpRaidEncounters.from(objectMapper.readTree("""
                [
                  { "enemies": ["Aegis_Drone", "Astrum", "Aegis_Drone"], "hpMultiplier": 0.6 },
                  ["Behemoth"]
                ]
                """));

        assertThat(encounters.encounters()).containsExactly(
                new Encounter(List.of("Aegis_Drone", "Astrum", "Aegis_Drone"), 0.6d),
                new Encounter(List.of("Behemoth"), 1d));
    }

    /**
     * Un zéro ou un négatif glissé par erreur donnerait un boss qui tombe au premier coup : ce n'est
     * pas un réglage, et l'échelle ordinaire est gardée.
     */
    @Test
    void anImpossibleMultiplierKeepsTheOrdinaryScale() throws Exception {
        StsPvpRaidEncounters encounters = StsPvpRaidEncounters.from(objectMapper.readTree("""
                [
                  { "enemies": ["A"], "hpMultiplier": 0 },
                  { "enemies": ["B"], "hpMultiplier": -2 },
                  { "enemies": ["C"], "hpMultiplier": "lots" },
                  { "enemies": ["D"] }
                ]
                """));

        assertThat(encounters.encounters()).extracting(Encounter::hpMultiplier)
                .containsExactly(1d, 1d, 1d, 1d);
    }

    /** Un même ennemi peut revenir plusieurs fois dans une rencontre : ce sont des combattants distincts. */
    @Test
    void keepsRepeatedEnemiesInAnEncounter() throws Exception {
        StsPvpRaidEncounters encounters = StsPvpRaidEncounters.from(
                objectMapper.readTree("[[\"Aegis_Drone\",\"Aegis_Drone\"]]"));

        assertThat(encounters.encounters().get(0).enemies()).hasSize(2);
    }

    @Test
    void anAbsentOrUnreadableFileHasNoEncounter() throws Exception {
        assertThat(StsPvpRaidEncounters.from(null).encounters()).isEmpty();
        assertThat(StsPvpRaidEncounters.from(objectMapper.createObjectNode()).encounters()).isEmpty();
        assertThat(StsPvpRaidEncounters.from(objectMapper.readTree("{\"encounters\":\"Behemoth\"}"))
                .encounters()).isEmpty();
    }

    /** Une rencontre sans ennemi ne pourrait jamais se terminer : elle est écartée, pas gardée vide. */
    @Test
    void dropsBlankIdsAndEncountersLeftEmpty() throws Exception {
        StsPvpRaidEncounters encounters = StsPvpRaidEncounters.from(objectMapper.readTree("""
                [["", "  "], [" Behemoth ", "", 3], "notAList", [],
                 { "hpMultiplier": 2 }, { "enemies": [] }]
                """));

        assertThat(encounters.encounters()).extracting(Encounter::enemies)
                .containsExactly(List.of("Behemoth"));
    }

    /**
     * Un ennemi inconnu ne se découvrirait qu'à l'ouverture du combat, joueurs déjà sortis de la
     * file : la rencontre qui le nomme est écartée du tirage, les autres restent jouables.
     */
    @Test
    void neverPicksAnEncounterNamingAnUnknownEnemy() throws Exception {
        StsPvpRaidEncounters encounters = StsPvpRaidEncounters.from(
                objectMapper.readTree("[[\"Behemoth\"],[\"Behemoth\",\"Typo\"]]"));
        Set<String> known = Set.of("Behemoth");

        for (int seed = 0; seed < 50; seed++) {
            assertThat(encounters.pick(new Random(seed), known::contains))
                    .map(Encounter::enemies)
                    .contains(List.of("Behemoth"));
        }
    }

    @Test
    void picksNothingWhenNoEncounterIsPlayable() throws Exception {
        StsPvpRaidEncounters encounters = StsPvpRaidEncounters.from(
                objectMapper.readTree("[[\"Typo\"]]"));

        assertThat(encounters.pick(new Random(1), Set.of("Behemoth")::contains)).isEmpty();
        assertThat(StsPvpRaidEncounters.NONE.pick(new Random(1), id -> true)).isEmpty();
    }

    /** Le tirage est au hasard : sur assez de raids, chaque rencontre finit par sortir. */
    @Test
    void everyPlayableEncounterCanComeUp() throws Exception {
        StsPvpRaidEncounters encounters = StsPvpRaidEncounters.from(
                objectMapper.readTree("[[\"A\"],[\"B\"],[\"C\",\"C\"]]"));
        Random random = new Random(42);
        Set<List<String>> seen = new HashSet<>();

        for (int draw = 0; draw < 200; draw++) {
            encounters.pick(random, id -> true).map(Encounter::enemies).ifPresent(seen::add);
        }

        assertThat(seen).containsExactlyInAnyOrder(List.of("A"), List.of("B"), List.of("C", "C"));
    }

    /** Le multiplicateur voyage avec la rencontre tirée, sans quoi le réglage ne servirait à rien. */
    @Test
    void thePickedEncounterCarriesItsMultiplier() throws Exception {
        StsPvpRaidEncounters encounters = StsPvpRaidEncounters.from(objectMapper.readTree(
                "[{\"enemies\":[\"Behemoth\"],\"hpMultiplier\":2.5}]"));

        assertThat(encounters.pick(new Random(7), id -> true))
                .map(Encounter::hpMultiplier)
                .contains(2.5d);
    }
}
