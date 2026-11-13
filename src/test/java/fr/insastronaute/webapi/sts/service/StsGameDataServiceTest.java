package fr.insastronaute.webapi.sts.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import fr.insastronaute.webapi.game.dto.GameConfigReleaseDto;
import fr.insastronaute.webapi.game.service.GameConfigService;
import fr.insastronaute.webapi.sts.combat.catalog.CardCatalogParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class StsGameDataServiceTest {

    private final GameConfigService gameConfigService = mock(GameConfigService.class);
    private StsGameDataService gameDataService;

    @BeforeEach
    void setUp() {
        gameDataService = new StsGameDataService(gameConfigService, new ObjectMapper().findAndRegisterModules());
        when(gameConfigService.latestRelease()).thenReturn(new GameConfigReleaseDto(
                "2026-07-09.1",
                "GameData/versions/2026-07-09.1/",
                List.of(
                        "catalogs/cards.json",
                        "catalogs/characters/index.json",
                        "catalogs/characters/AI.json",
                        "catalogs/characters/MECA.json"
                ),
                Instant.parse("2026-07-09T10:00:00Z")
        ));
        when(gameConfigService.readReleaseFile("2026-07-09.1", "catalogs/characters/AI.json"))
                .thenReturn("{\"characterId\":\"AI\",\"hp\":62}");
        when(gameConfigService.readReleaseFile("2026-07-09.1", "catalogs/characters/MECA.json"))
                .thenReturn("{\"characterId\":\"MECA\",\"hp\":70}");
        when(gameConfigService.readReleaseFile("2026-07-09.1", "catalogs/cards.json"))
                .thenReturn("{\"cards\":[{\"id\":\"strike\"}]}");
    }

    @Test
    void charactersLoadsCharacterFilesFromLatestReleaseAndSkipsIndex() {
        ArrayNode characters = gameDataService.characters(null);

        assertThat(characters).hasSize(2);
        assertThat(characters.get(0).path("characterId").asText()).isEqualTo("AI");
        assertThat(characters.get(1).path("characterId").asText()).isEqualTo("MECA");
    }

    @Test
    void characterValidatesRequestedCharacter() {
        assertThat(gameDataService.character(null, "MECA").path("hp").asInt()).isEqualTo(70);
    }

    @Test
    void cardsReturnsRawCardsCatalog() {
        assertThat(gameDataService.cards(null).path("cards").get(0).path("id").asText()).isEqualTo("strike");
    }

    @Test
    void cachesImmutableReleaseDataByVersion() {
        gameDataService.cards("2026-07-09.1");
        gameDataService.cards("2026-07-09.1");

        verify(gameConfigService, times(1))
                .readReleaseFile("2026-07-09.1", "catalogs/cards.json");
    }

    @Test
    void resolvesAnAvailableHistoricalReleaseForRunCatalogs() {
        GameConfigReleaseDto latest = new GameConfigReleaseDto(
                "2026-07-10.1", "GameData/versions/2026-07-10.1/", List.of(),
                Instant.parse("2026-07-10T10:00:00Z"));
        GameConfigReleaseDto pinned = new GameConfigReleaseDto(
                "2026-07-09.1", "GameData/versions/2026-07-09.1/", List.of(),
                Instant.parse("2026-07-09T10:00:00Z"));
        when(gameConfigService.latestRelease()).thenReturn(latest);
        when(gameConfigService.listReleases()).thenReturn(List.of(latest, pinned));

        assertThat(gameDataService.resolveVersion("2026-07-09.1")).isEqualTo("2026-07-09.1");
    }

    @Test
    void doesNotReplaceAnUnavailablePinnedReleaseWithLatest() {
        when(gameConfigService.latestRelease()).thenReturn(new GameConfigReleaseDto(
                "2026-07-10.1", "GameData/versions/2026-07-10.1/", List.of(),
                Instant.parse("2026-07-10T10:00:00Z")));
        when(gameConfigService.listReleases()).thenReturn(List.of());

        assertThatThrownBy(() -> gameDataService.resolveVersion("missing-release"))
                .isInstanceOf(fr.insastronaute.webapi.common.web.NotFoundException.class);
    }

    @Test
    void runScoreRewardsLoadsVersionedGameplayConfiguration() {
        when(gameConfigService.latestRelease()).thenReturn(new GameConfigReleaseDto(
                "2026-07-10.1",
                "GameData/versions/2026-07-10.1/",
                List.of("gameplay/run_score_rewards.json"),
                Instant.parse("2026-07-10T10:00:00Z")
        ));
        when(gameConfigService.readReleaseFile("2026-07-10.1", "gameplay/run_score_rewards.json"))
                .thenReturn("{\"schemaVersion\":1,\"tokens\":{\"scorePerToken\":20}}");

        assertThat(gameDataService.runScoreRewards(null).path("tokens").path("scorePerToken").asInt())
                .isEqualTo(20);
    }

    @Test
    void cardsCanLoadUnityStreamingAssetsCardsCatalog() {
        when(gameConfigService.latestRelease()).thenReturn(new GameConfigReleaseDto(
                "2026-07-10.1",
                "GameData/versions/2026-07-10.1/",
                List.of("STSCardData/cards.json"),
                Instant.parse("2026-07-10T10:00:00Z")
        ));
        when(gameConfigService.readReleaseFile("2026-07-10.1", "STSCardData/cards.json"))
                .thenReturn("{\"cards\":[{\"id\":\"katana\"}]}");

        assertThat(gameDataService.cards(null).path("cards").get(0).path("id").asText()).isEqualTo("katana");
    }

    @Test
    void cardsPreferIndividualStreamingAssetsFilesOverCombinedCardsJson() {
        when(gameConfigService.latestRelease()).thenReturn(new GameConfigReleaseDto(
                "2026-07-11.1",
                "GameData/versions/2026-07-11.1/",
                List.of(
                        "STSCardData/Strike.json",
                        "STSCardData/Guard.json",
                        "STSCardData/cards.json"
                ),
                Instant.parse("2026-07-11T10:00:00Z")
        ));
        when(gameConfigService.readReleaseFile("2026-07-11.1", "STSCardData/Strike.json"))
                .thenReturn("""
                        {"id":"strike","cardName":"Strike from file","collectionCardId":"Base",
                         "multiplayerExclusive":false,"characterLevel":0,"cost":1,"xCost":false,
                         "type":"Attaque","rarity":"Common","targetingMode":"Enemy",
                         "effects":[{"type":"Damage","value":6}],"modifiers":[],"tags":[]}
                        """);
        when(gameConfigService.readReleaseFile("2026-07-11.1", "STSCardData/Guard.json"))
                .thenReturn("""
                        {"id":"guard","cardName":"Guard from file","cost":1,"xCost":false,
                         "type":"Compétence","targetingMode":"Player",
                         "effects":[{"type":"Armor","value":5}],"modifiers":[],"tags":[]}
                        """);
        when(gameConfigService.readReleaseFile("2026-07-11.1", "STSCardData/cards.json"))
                .thenReturn("{\"cards\":[{\"id\":\"strike\",\"cardName\":\"Strike from combined\"}]}");

        var catalog = gameDataService.cards(null);
        assertThat(catalog.path("dataVersion").asText()).isEqualTo("2026-07-11.1");
        assertThat(catalog.path("cards")).hasSize(2);
        assertThat(catalog.path("cards").get(0).path("cardName").asText())
                .isEqualTo("Guard from file");
        assertThat(catalog.path("cards").get(1).path("cardName").asText())
                .isEqualTo("Strike from file");
        assertThat(new CardCatalogParser().parse(catalog).dataVersion()).isEqualTo("2026-07-11.1");
    }

    @Test
    void cardsUseCombinedCardsJsonOnlyForMissingEntriesWhenIndividualFilesExist() {
        when(gameConfigService.latestRelease()).thenReturn(new GameConfigReleaseDto(
                "2026-07-11.2",
                "GameData/versions/2026-07-11.2/",
                List.of(
                        "STSCardData/Strike.json",
                        "STSCardData/cards.json"
                ),
                Instant.parse("2026-07-11T11:00:00Z")
        ));
        when(gameConfigService.readReleaseFile("2026-07-11.2", "STSCardData/Strike.json"))
                .thenReturn("{\"id\":\"strike\",\"cardName\":\"Strike from file\"}");
        when(gameConfigService.readReleaseFile("2026-07-11.2", "STSCardData/cards.json"))
                .thenReturn("{\"cards\":[{\"id\":\"strike\",\"cardName\":\"Strike from combined\"},{\"id\":\"katana\",\"cardName\":\"Katana from combined\"}]}");

        assertThat(gameDataService.cards(null).path("cards")).hasSize(2);
        assertThat(gameDataService.cards(null).path("cards").get(0).path("cardName").asText())
                .isEqualTo("Strike from file");
        assertThat(gameDataService.cards(null).path("cards").get(1).path("cardName").asText())
                .isEqualTo("Katana from combined");
    }

    @Test
    void charactersCanLoadUnityStreamingAssetsPlayerFilesAndSkipsIndex() {
        when(gameConfigService.latestRelease()).thenReturn(new GameConfigReleaseDto(
                "2026-07-10.1",
                "GameData/versions/2026-07-10.1/",
                List.of(
                        "Players/index.json",
                        "Players/ITI.json",
                        "Players/MECA.json"
                ),
                Instant.parse("2026-07-10T10:00:00Z")
        ));
        when(gameConfigService.readReleaseFile("2026-07-10.1", "Players/ITI.json"))
                .thenReturn("{\"characterId\":\"ITI\",\"hp\":85}");
        when(gameConfigService.readReleaseFile("2026-07-10.1", "Players/MECA.json"))
                .thenReturn("{\"characterId\":\"MECA\",\"hp\":70}");

        ArrayNode characters = gameDataService.characters(null);

        assertThat(characters).hasSize(2);
        assertThat(characters.get(0).path("characterId").asText()).isEqualTo("ITI");
        assertThat(characters.get(1).path("characterId").asText()).isEqualTo("MECA");
    }

    @Test
    void enemiesCanLoadUnityStreamingAssetsEnemyDataFilesAndSkipsIndex() {
        when(gameConfigService.latestRelease()).thenReturn(new GameConfigReleaseDto(
                "2026-07-10.1",
                "GameData/versions/2026-07-10.1/",
                List.of(
                        "EnemyData/index.json",
                        "EnemyData/Slime.json",
                        "EnemyData/Elite.json"
                ),
                Instant.parse("2026-07-10T10:00:00Z")
        ));
        when(gameConfigService.readReleaseFile("2026-07-10.1", "EnemyData/Elite.json"))
                .thenReturn("{\"enemyId\":\"Elite\",\"maxHp\":80}");
        when(gameConfigService.readReleaseFile("2026-07-10.1", "EnemyData/Slime.json"))
                .thenReturn("{\"enemyId\":\"Slime\",\"maxHp\":20}");

        ArrayNode enemies = gameDataService.enemies(null);

        assertThat(enemies).hasSize(2);
        assertThat(enemies.get(0).path("enemyId").asText()).isEqualTo("Elite");
        assertThat(enemies.get(1).path("enemyId").asText()).isEqualTo("Slime");
    }

    @Test
    void enemiesCanLoadLegacyEnemiesCatalogArray() {
        when(gameConfigService.latestRelease()).thenReturn(new GameConfigReleaseDto(
                "2026-07-09.1",
                "GameData/versions/2026-07-09.1/",
                List.of("catalogs/enemies.json"),
                Instant.parse("2026-07-09T10:00:00Z")
        ));
        when(gameConfigService.readReleaseFile("2026-07-09.1", "catalogs/enemies.json"))
                .thenReturn("{\"enemies\":[{\"enemyId\":\"Legacy Slime\"}]}");

        assertThat(gameDataService.enemies(null).get(0).path("enemyId").asText()).isEqualTo("Legacy Slime");
    }

    @Test
    void eventsCanLoadUnityStreamingAssetsEventData() {
        when(gameConfigService.latestRelease()).thenReturn(new GameConfigReleaseDto(
                "2026-07-10.1",
                "GameData/versions/2026-07-10.1/",
                List.of("Events/EventData.json"),
                Instant.parse("2026-07-10T10:00:00Z")
        ));
        when(gameConfigService.readReleaseFile("2026-07-10.1", "Events/EventData.json"))
                .thenReturn("{\"events\":[{\"title\":\"Bar ITI-GM\"}]}");

        assertThat(gameDataService.events(null).path("events").get(0).path("title").asText()).isEqualTo("Bar ITI-GM");
    }

    @Test
    void relicsCanLoadUnityStreamingAssetsRelicsCatalogWhenPresent() {
        when(gameConfigService.latestRelease()).thenReturn(new GameConfigReleaseDto(
                "2026-07-10.1",
                "GameData/versions/2026-07-10.1/",
                List.of("Relics/relics.json"),
                Instant.parse("2026-07-10T10:00:00Z")
        ));
        when(gameConfigService.readReleaseFile("2026-07-10.1", "Relics/relics.json"))
                .thenReturn("{\"relics\":[{\"id\":\"PortableWorkshopRelic\"}]}");

        assertThat(gameDataService.relics(null).path("relics").get(0).path("id").asText()).isEqualTo("PortableWorkshopRelic");
    }

    @Test
    void encounterTablesCanBeDerivedFromUnityStreamingAssetsEnemyPool() {
        when(gameConfigService.latestRelease()).thenReturn(new GameConfigReleaseDto(
                "2026-07-10.1",
                "GameData/versions/2026-07-10.1/",
                List.of("EnemyPool/EnemyPool.json"),
                Instant.parse("2026-07-10T10:00:00Z")
        ));
        when(gameConfigService.readReleaseFile("2026-07-10.1", "EnemyPool/EnemyPool.json"))
                .thenReturn("""
                        {
                          "enemies": [
                            {"displayName":"Hallway","enemyIds":["Enemy 1"],"weight":1.0,"elite":false,"boss":false},
                            {"displayName":"Elite","enemyIds":["Elite"],"weight":2.0,"elite":true,"boss":false},
                            {"displayName":"Boss","enemyIds":["Boss"],"weight":3.0,"elite":false,"boss":true}
                          ]
                        }
                        """);

        assertThat(gameDataService.encounterTables(null).path("tables")).hasSize(3);
        assertThat(gameDataService.encounterTables(null).path("tables").get(0).path("nodeTypes").get(0).asText()).isEqualTo("Combat");
        assertThat(gameDataService.encounterTables(null).path("tables").get(1).path("entries").get(0).path("encounterId").asText()).isEqualTo("Elite");
        assertThat(gameDataService.encounterTables(null).path("tables").get(2).path("entries").get(0).path("enemyIds").get(0).asText()).isEqualTo("Boss");
    }

    @Test
    void encounterTablesPreferUnityStreamingAssetsEnemyPoolOverLegacyTables() {
        when(gameConfigService.latestRelease()).thenReturn(new GameConfigReleaseDto(
                "2026-07-10.1",
                "GameData/versions/2026-07-10.1/",
                List.of("EnemyPool/EnemyPool.json", "gameplay/encounter_tables.json"),
                Instant.parse("2026-07-10T10:00:00Z")
        ));
        when(gameConfigService.readReleaseFile("2026-07-10.1", "EnemyPool/EnemyPool.json"))
                .thenReturn("""
                        {
                          "enemies": [
                            {"displayName":"Pool Combat","enemyIds":["Pool Enemy"],"weight":1.0,"elite":false,"boss":false}
                          ]
                        }
                        """);

        assertThat(gameDataService.encounterTables(null).path("tables").get(0).path("entries").get(0).path("encounterId").asText())
                .isEqualTo("Pool Combat");
    }

    @Test
    void enemyPoolReturnsRawUnityStreamingAssetsDocument() {
        when(gameConfigService.latestRelease()).thenReturn(new GameConfigReleaseDto(
                "2026-07-10.1",
                "GameData/versions/2026-07-10.1/",
                List.of("EnemyPool/EnemyPool.json"),
                Instant.parse("2026-07-10T10:00:00Z")
        ));
        when(gameConfigService.readReleaseFile("2026-07-10.1", "EnemyPool/EnemyPool.json"))
                .thenReturn("""
                        {
                          "schemaVersion": 3,
                          "enemies": [
                            {"displayName":"Pool Combat","enemyIds":["Pool Enemy"],"customUnityField":"preserved"}
                          ]
                        }
                        """);

        assertThat(gameDataService.enemyPool(null).path("schemaVersion").asInt()).isEqualTo(3);
        assertThat(gameDataService.enemyPool(null).path("enemies").get(0).path("displayName").asText())
                .isEqualTo("Pool Combat");
        assertThat(gameDataService.enemyPool(null).path("enemies").get(0).path("customUnityField").asText())
                .isEqualTo("preserved");
    }

    /**
     * L'acte de départ s'écrit comme le joueur le lit.
     *
     * <p>{@code "startingAct": 3} démarre sur l'Acte 3, qui est l'index interne 2. Les
     * {@code minAct}/{@code maxAct} des entrées du même fichier sont, eux, des index internes :
     * c'est la seule asymétrie du fichier, et elle est voulue — ce réglage-ci se pose en pensant
     * à un numéro d'acte affiché.
     */
    @Test
    void startingActIsAuthoredAsThePlayerNumbersTheActs() {
        givenEnemyPool("""
                {"maxAct": 3, "startingAct": 3, "enemies": []}
                """);

        assertThat(gameDataService.startingAct(null)).isEqualTo(2);
    }

    /** Sans réglage, une run démarre au premier acte, comme elle l'a toujours fait. */
    @Test
    void anEnemyPoolWithoutAStartingActBeginsOnTheFirstOne() {
        givenEnemyPool("""
                {"maxAct": 3, "enemies": []}
                """);

        assertThat(gameDataService.startingAct(null)).isZero();
    }

    /**
     * Un acte de départ au-delà du dernier acte est ramené au dernier.
     *
     * <p>Sinon la run démarrerait sur un acte dont aucune entrée d'encounter ne peut rien tirer,
     * et le premier combat échouerait au lieu du réglage.
     */
    @Test
    void aStartingActPastTheLastOneIsBroughtBackToIt() {
        givenEnemyPool("""
                {"maxAct": 3, "startingAct": 9, "enemies": []}
                """);

        assertThat(gameDataService.startingAct(null)).isEqualTo(2);
    }

    /** Un enemy pool illisible ne doit pas empêcher une run de démarrer ni de se terminer. */
    @Test
    void anUnreadableEnemyPoolStillAnswersWithTheFirstActAndOneAct() {
        assertThat(gameDataService.startingAct("2026-07-09.1")).isZero();
        assertThat(gameDataService.actCount("2026-07-09.1")).isEqualTo(1);
    }

    private void givenEnemyPool(String json) {
        when(gameConfigService.latestRelease()).thenReturn(new GameConfigReleaseDto(
                "2026-07-10.1",
                "GameData/versions/2026-07-10.1/",
                List.of("EnemyPool/EnemyPool.json"),
                Instant.parse("2026-07-10T10:00:00Z")
        ));
        when(gameConfigService.readReleaseFile("2026-07-10.1", "EnemyPool/EnemyPool.json"))
                .thenReturn(json);
    }
}
