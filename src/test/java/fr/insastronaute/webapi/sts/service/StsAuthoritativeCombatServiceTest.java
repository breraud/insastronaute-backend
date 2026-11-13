package fr.insastronaute.webapi.sts.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.sts.combat.api.CombatCommandMetrics;
import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentEngine;
import fr.insastronaute.webapi.sts.dto.StsCombatCommandRequest;
import fr.insastronaute.webapi.sts.dto.StsCombatCommandResponse;
import fr.insastronaute.webapi.sts.domain.StsRun;
import fr.insastronaute.webapi.user.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StsAuthoritativeCombatServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final StsGameDataService gameDataService = mock(StsGameDataService.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final StsAuthoritativeCombatService service = new StsAuthoritativeCombatService(
            gameDataService,
            objectMapper,
            List.of(),
            new EnchantmentEngine(List.of()),
            new CombatCommandMetrics(meterRegistry));

    @Test
    void measuresTheInternalPhasesOfAnAcceptedCommand() throws Exception {
        StsRun run = runWithPlayerState("{\"currentHp\":70,\"maxHp\":70}");
        JsonNode combat = service.createCombatState(run);
        run.setActiveCombat(combat);

        service.submitCommand(run, new StsCombatCommandRequest(
                "END_TURN", combat.path("revision").asLong(), null, List.of(), List.of()));

        assertThat(meterRegistry.find("sts.combat.command.duration").timers().stream()
                .filter(timer -> timer.count() > 0)
                .map(timer -> timer.getId().getTag("phase")))
                .contains("deserialize", "catalog", "decode", "player_engine", "ai", "serialize");
    }

    @Test
    void logsTheStoredCombatJsonWhenCommandProcessingFails() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = new StsRun();
        ReflectionTestUtils.setField(run, "id", runId);
        run.setActiveCombat(objectMapper.readTree("{\"broken\":\"snapshot-marker\"}"));
        StsCombatCommandRequest request = new StsCombatCommandRequest(
                "END_TURN", 7L, null, List.of(), List.of());
        ListAppender<ILoggingEvent> log = captureLog();

        try {
            assertThatThrownBy(() -> service.submitCommand(run, request))
                    .isInstanceOf(RuntimeException.class);

            assertThat(log.list).filteredOn(event -> event.getLevel() == Level.ERROR)
                    .singleElement()
                    .satisfies(event -> assertThat(event.getFormattedMessage())
                            .contains("run=" + runId)
                            .contains("command=END_TURN")
                            .contains("combatState={\"broken\":\"snapshot-marker\"}"));
        } finally {
            releaseLog(log);
        }
    }

    /**
     * The boss that did nothing for a whole fight. Its two authored moves declare neither a
     * {@code cardId} nor effects, so both are dropped and the pattern that reaches the AI is
     * empty. The fight still has to run, so nothing throws; but building that pattern is the one
     * place that knows how many moves were authored and why each was lost, so it is the place
     * that has to say so.
     */
    @Test
    void reportsEveryMoveItDropsAndTheEnemyItLeavesWithoutAnyMove() throws Exception {
        StsRun run = dragonRunWithUnusableMoves();
        ListAppender<ILoggingEvent> log = captureLog();

        try {
            var state = service.createCombatState(run);

            assertThat(state.path("combatants")).hasSize(2);

            assertThat(log.list).filteredOn(event -> event.getLevel() == Level.WARN)
                    .hasSize(2)
                    .allSatisfy(event -> assertThat(event.getFormattedMessage()).contains("Dragon"));
            assertThat(log.list).filteredOn(event -> event.getLevel() == Level.WARN)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message).contains("movePattern[0]"))
                    .anySatisfy(message -> assertThat(message).contains("movePattern[1]"));

            assertThat(log.list).filteredOn(event -> event.getLevel() == Level.ERROR)
                    .singleElement()
                    .satisfies(event -> assertThat(event.getFormattedMessage())
                            .contains("Dragon")
                            .contains("enemy-0")
                            .contains("2"));
        } finally {
            releaseLog(log);
        }
    }

    /**
     * The pattern is rebuilt for every single command the player sends, so reporting the defect
     * each time would write the same lines dozens of times per fight and again for every player
     * who meets that enemy. The defect belongs to the data, not to the run, so it is said once.
     */
    @Test
    void reportsTheSameContentDefectOnlyOnce() throws Exception {
        StsRun run = dragonRunWithUnusableMoves();
        ListAppender<ILoggingEvent> log = captureLog();

        try {
            service.createCombatState(run);
            service.createCombatState(run);
            service.createCombatState(run);

            assertThat(log.list).filteredOn(event -> event.getLevel() == Level.ERROR).hasSize(1);
            assertThat(log.list).filteredOn(event -> event.getLevel() == Level.WARN).hasSize(2);
        } finally {
            releaseLog(log);
        }
    }

    /** A healthy encounter says nothing. */
    @Test
    void staysSilentForAnEnemyWhoseMovesAllParse() throws Exception {
        StsRun run = new StsRun();
        ReflectionTestUtils.setField(run, "id", UUID.randomUUID());
        run.setDataVersion("2026-08-20.1");
        run.setPlayerState(objectMapper.readTree("{\"currentHp\":70,\"maxHp\":70}"));
        run.setRunInventory(objectMapper.readTree("""
                {"deck":[{"instanceId":"card-1","cardId":"strike","enchantments":[]}],"relics":[]}
                """));
        run.setActiveEncounter(objectMapper.readTree("""
                {"encounterInstanceId":"enc-1","enemyIds":["slime"]}
                """));
        when(gameDataService.cards("2026-08-20.1")).thenReturn(objectMapper.readTree("""
                {"dataVersion":"2026-08-20.1","cards":[
                  {"id":"strike","cost":1,"targetingMode":"Enemy",
                   "effects":[{"type":"Damage","value":6}]}
                ]}
                """));
        when(gameDataService.enemies("2026-08-20.1")).thenReturn((com.fasterxml.jackson.databind.node.ArrayNode)
                objectMapper.readTree("""
                        [{"id":"slime","maxHP":20,
                          "movePattern":[{"effects":[{"type":"Damage","value":3}],"nextMoveIndices":[0]}]}]
                        """));

        ListAppender<ILoggingEvent> log = captureLog();
        try {
            service.createCombatState(run);

            assertThat(log.list).isEmpty();
        } finally {
            releaseLog(log);
        }
    }

    private StsRun dragonRunWithUnusableMoves() throws Exception {
        StsRun run = new StsRun();
        ReflectionTestUtils.setField(run, "id", UUID.randomUUID());
        run.setDataVersion("2026-08-20.1");
        run.setPlayerState(objectMapper.readTree("{\"currentHp\":70,\"maxHp\":70}"));
        run.setRunInventory(objectMapper.readTree("""
                {"deck":[{"instanceId":"card-1","cardId":"strike","enchantments":[]}],"relics":[]}
                """));
        run.setActiveEncounter(objectMapper.readTree("""
                {"encounterInstanceId":"enc-dragon","enemyIds":["Dragon"]}
                """));

        when(gameDataService.cards("2026-08-20.1")).thenReturn(objectMapper.readTree("""
                {"dataVersion":"2026-08-20.1","cards":[
                  {"id":"strike","cost":1,"targetingMode":"Enemy",
                   "effects":[{"type":"Damage","value":6}]}
                ]}
                """));
        when(gameDataService.enemies("2026-08-20.1")).thenReturn((com.fasterxml.jackson.databind.node.ArrayNode)
                objectMapper.readTree("""
                        [{"id":"Dragon","maxHP":250,"movePattern":[
                          {"nextMoveIndices":[1]},
                          {"nextMoveIndices":[0]}
                        ]}]
                        """));
        return run;
    }

    /**
     * The log is the behaviour under test, so it is read where it is written: a Logback appender
     * on the real logger checks the level and the rendered message, which an extracted flag could
     * not.
     */
    private static ListAppender<ILoggingEvent> captureLog() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(StsAuthoritativeCombatService.class))
                .addAppender(appender);
        return appender;
    }

    private static void releaseLog(ListAppender<ILoggingEvent> appender) {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(StsAuthoritativeCombatService.class))
                .detachAppender(appender);
        appender.stop();
    }

    @Test
    void createsCombatWithoutParsingUnrelatedUnsupportedCards() throws Exception {
        UUID runId = UUID.randomUUID();
        StsRun run = new StsRun();
        ReflectionTestUtils.setField(run, "id", runId);
        run.setDataVersion("2026-08-20.1");
        run.setPlayerState(objectMapper.readTree("{\"currentHp\":70,\"maxHp\":70}"));
        run.setRunInventory(objectMapper.readTree("""
                {"deck":[{"instanceId":"card-1","cardId":"strike","enchantments":[]}],"relics":[]}
                """));
        run.setActiveEncounter(objectMapper.readTree("""
                {"encounterInstanceId":"enc-1","enemyIds":["slime"]}
                """));

        when(gameDataService.cards("2026-08-20.1")).thenReturn(objectMapper.readTree("""
                {"dataVersion":"2026-08-20.1","cards":[
                  {"id":"strike","cost":1,"targetingMode":"Enemy",
                   "effects":[{"type":"Damage","value":6}]},
                  {"id":"unrelated-aoe","cost":1,"targetingMode":"AllEnemies",
                   "effects":[{"type":"Damage","value":99}]}
                ]}
                """));
        when(gameDataService.enemies("2026-08-20.1")).thenReturn((com.fasterxml.jackson.databind.node.ArrayNode)
                objectMapper.readTree("""
                        [{"id":"slime","maxHP":20,
                          "movePattern":[{"effects":[{"type":"Damage","value":3}],"nextMoveIndices":[0]}]}]
                        """));

        var state = service.createCombatState(run);

        assertThat(state.path("combatId").asText()).isNotBlank();
        assertThat(state.path("combatants")).hasSize(2);
    }

    @Test
    void loadsTheCardsThatEffectsCreateEvenWhenNobodyOwnsThem() throws Exception {
        StsRun run = new StsRun();
        ReflectionTestUtils.setField(run, "id", UUID.randomUUID());
        run.setDataVersion("2026-08-20.1");
        run.setPlayerState(objectMapper.readTree("{\"currentHp\":70,\"maxHp\":70}"));
        run.setRunInventory(objectMapper.readTree("""
                {"deck":[{"instanceId":"card-1","cardId":"sabotage","enchantments":[]}],"relics":[]}
                """));
        run.setActiveEncounter(objectMapper.readTree("""
                {"encounterInstanceId":"enc-1","enemyIds":["slime"]}
                """));

        when(gameDataService.cards("2026-08-20.1")).thenReturn(objectMapper.readTree("""
                {"dataVersion":"2026-08-20.1","cards":[
                  {"id":"sabotage","cost":1,"targetingMode":"Enemy",
                   "effects":[{"type":"AddCardToDiscardPile","value":1,"cardID":"rouille"}]},
                  {"id":"rouille","cost":0,"type":"Rien","targetingMode":"None",
                   "effects":[{"type":"AddCardToDrawPile","value":1,"cardID":"poussiere"}]},
                  {"id":"poussiere","cost":0,"type":"Rien","targetingMode":"None",
                   "effects":[{"type":"LoseHP","value":1,"targetSelf":true}]},
                  {"id":"jamais-utilisee","cost":1,"targetingMode":"Enemy",
                   "effects":[{"type":"Damage","value":99}]}
                ]}
                """));
        when(gameDataService.enemies("2026-08-20.1")).thenReturn((com.fasterxml.jackson.databind.node.ArrayNode)
                objectMapper.readTree("""
                        [{"id":"slime","maxHP":20,
                          "movePattern":[{"effects":[{"type":"AddCardToHand","value":1,"cardID":"rouille"}],
                                          "nextMoveIndices":[0]}]}]
                        """));

        var catalog = ReflectionTestUtils.invokeMethod(service, "catalogForRun", run);
        var cards = ((fr.insastronaute.webapi.sts.combat.catalog.CardCatalog) catalog).cards();

        assertThat(cards.keySet()).contains("sabotage", "rouille", "poussiere");
        assertThat(cards.keySet()).doesNotContain("jamais-utilisee");
    }

    @Test
    void createsCombatFromDonuStatusAndMultihitMoves() throws Exception {
        StsRun run = new StsRun();
        ReflectionTestUtils.setField(run, "id", UUID.randomUUID());
        run.setDataVersion("2026-08-20.1");
        run.setPlayerState(objectMapper.readTree("{\"currentHp\":70,\"maxHp\":70}"));
        run.setRunInventory(objectMapper.readTree("""
                {"deck":[{"instanceId":"card-1","cardId":"strike","enchantments":[]}],"relics":[]}
                """));
        run.setActiveEncounter(objectMapper.readTree("""
                {"encounterInstanceId":"enc-donu","enemyIds":["Donu"]}
                """));

        when(gameDataService.cards("2026-08-20.1")).thenReturn(objectMapper.readTree("""
                {"dataVersion":"2026-08-20.1","cards":[
                  {"id":"strike","cost":1,"targetingMode":"Enemy",
                   "effects":[{"type":"Damage","value":6}]}
                ]}
                """));
        when(gameDataService.enemies("2026-08-20.1")).thenReturn((com.fasterxml.jackson.databind.node.ArrayNode)
                objectMapper.readTree("""
                        [{"id":"Donu","maxHP":265,"movePattern":[
                          {"effects":[{"type":"Status","statusType":"Strength","value":3,
                                       "targetSelf":true,"duration":2}],"nextMoveIndices":[1]},
                          {"effects":[{"type":"Multihit","value":3,"duration":2}],"nextMoveIndices":[2]},
                          {"effects":[{"type":"Status","statusType":"Strength","value":-1,
                                       "duration":3}],"nextMoveIndices":[0]}
                        ]}]
                        """));

        var state = service.createCombatState(run);

        assertThat(state.path("combatId").asText()).isNotBlank();
        assertThat(state.path("combatants")).hasSize(2);
    }

    @Test
    void createsCombatFromAsteroidAdeptDispelAllMove() throws Exception {
        StsRun run = new StsRun();
        ReflectionTestUtils.setField(run, "id", UUID.randomUUID());
        run.setDataVersion("2026-08-20.1");
        run.setPlayerState(objectMapper.readTree("{\"currentHp\":70,\"maxHp\":70}"));
        run.setRunInventory(objectMapper.readTree("""
                {"deck":[{"instanceId":"card-1","cardId":"strike","enchantments":[]}],"relics":[]}
                """));
        run.setActiveEncounter(objectMapper.readTree("""
                {"encounterInstanceId":"enc-asteroid","enemyIds":["Asteroid_Adept"]}
                """));

        when(gameDataService.cards("2026-08-20.1")).thenReturn(objectMapper.readTree("""
                {"dataVersion":"2026-08-20.1","cards":[
                  {"id":"strike","cost":1,"targetingMode":"Enemy",
                   "effects":[{"type":"Damage","value":6}]}
                ]}
                """));
        when(gameDataService.enemies("2026-08-20.1")).thenReturn((com.fasterxml.jackson.databind.node.ArrayNode)
                objectMapper.readTree("""
                        [{"id":"Asteroid_Adept","maxHP":16,"movePattern":[
                          {"effects":[{"type":"DispelDebuff","value":-1,"targetSelf":true}],
                           "nextMoveIndices":[0]}
                        ]}]
                        """));

        var state = service.createCombatState(run);

        assertThat(state.path("combatId").asText()).isNotBlank();
        assertThat(state.path("combatants")).hasSize(2);
    }

    @Test
    void startsTheCombatOnTheHpTheRunCarriesRatherThanOnFullHealth() throws Exception {
        StsRun run = runWithPlayerState("{\"currentHp\":31,\"maxHp\":70}");

        JsonNode player = playerCombatant(service.createCombatState(run));

        assertThat(player.path("maxHp").asInt()).isEqualTo(70);
        assertThat(player.path("hp").asInt()).isEqualTo(31);
    }

    @Test
    void startsAFreshRunAtFullHealthWhenItCarriesNoCurrentHp() throws Exception {
        StsRun run = runWithPlayerState("{\"maxHp\":70}");

        JsonNode player = playerCombatant(service.createCombatState(run));

        assertThat(player.path("hp").asInt()).isEqualTo(70);
    }

    @Test
    void startsAtFullHealthRatherThanDeadWhenTheRunCarriesANonPositiveCurrentHp() throws Exception {
        JsonNode zero = playerCombatant(
                service.createCombatState(runWithPlayerState("{\"currentHp\":0,\"maxHp\":70}")));
        JsonNode negative = playerCombatant(
                service.createCombatState(runWithPlayerState("{\"currentHp\":-5,\"maxHp\":70}")));
        JsonNode nullHp = playerCombatant(
                service.createCombatState(runWithPlayerState("{\"currentHp\":null,\"maxHp\":70}")));

        assertThat(zero.path("hp").asInt()).isEqualTo(70);
        assertThat(negative.path("hp").asInt()).isEqualTo(70);
        assertThat(nullHp.path("hp").asInt()).isEqualTo(70);
    }

    @Test
    void capsTheStartingHpAtMaxHpWhenTheRunCarriesMore() throws Exception {
        StsRun run = runWithPlayerState("{\"currentHp\":999,\"maxHp\":70}");

        JsonNode player = playerCombatant(service.createCombatState(run));

        assertThat(player.path("hp").asInt()).isEqualTo(70);
    }

    @Test
    void startsEnemiesAtFullHealthWhateverThePlayerHpIs() throws Exception {
        StsRun run = runWithPlayerState("{\"currentHp\":3,\"maxHp\":70}");

        JsonNode enemy = combatant(service.createCombatState(run), "enemy-0");

        assertThat(enemy.path("maxHp").asInt()).isEqualTo(20);
        assertThat(enemy.path("hp").asInt()).isEqualTo(20);
    }

    /**
     * La carte qui ouvre le pool secret l'ouvre pour le compte, pas pour la run.
     *
     * <p>C'est la seule porte d'entrée des cartes {@code Secret} dans les tables de récompense :
     * tant que rien ne l'a franchie, elles n'existent pour personne.
     */
    @Test
    void playingACardTaggedSecretUnlockOpensTheSecretPoolForTheAccount() throws Exception {
        AppUser owner = new AppUser();
        StsRun run = runWithSecretUnlockCard(owner);
        JsonNode combat = service.createCombatState(run);
        run.setActiveCombat(combat);

        StsCombatCommandResponse response = service.submitCommand(run, new StsCombatCommandRequest(
                "PLAY_CARD", combat.path("revision").asLong(), "card-1", List.of("enemy-0"), List.of()));

        assertThat(response.accepted()).isTrue();
        assertThat(owner.hasUnlockedStsSecretCards()).isTrue();
    }

    /** Une carte ordinaire n'ouvre rien : seul le marqueur compte, pas le fait d'avoir joué. */
    @Test
    void playingAnOrdinaryCardLeavesTheSecretPoolClosed() throws Exception {
        AppUser owner = new AppUser();
        StsRun run = runWithPlayerState("{\"currentHp\":70,\"maxHp\":70}");
        run.setOwner(owner);
        JsonNode combat = service.createCombatState(run);
        run.setActiveCombat(combat);

        service.submitCommand(run, new StsCombatCommandRequest(
                "PLAY_CARD", combat.path("revision").asLong(), "card-1", List.of("enemy-0"), List.of()));

        assertThat(owner.hasUnlockedStsSecretCards()).isFalse();
    }

    /**
     * Une commande refusée n'a rien joué.
     *
     * <p>Le déverrouillage ne se reprend jamais : l'accorder sur une carte que le moteur vient de
     * refuser le donnerait pour de bon à qui rejoue une révision périmée.
     */
    @Test
    void aRejectedPlayDoesNotOpenTheSecretPool() throws Exception {
        AppUser owner = new AppUser();
        StsRun run = runWithSecretUnlockCard(owner);
        JsonNode combat = service.createCombatState(run);
        run.setActiveCombat(combat);

        StsCombatCommandResponse response = service.submitCommand(run, new StsCombatCommandRequest(
                "PLAY_CARD", combat.path("revision").asLong() + 5, "card-1", List.of("enemy-0"), List.of()));

        assertThat(response.accepted()).isFalse();
        assertThat(owner.hasUnlockedStsSecretCards()).isFalse();
    }

    private StsRun runWithSecretUnlockCard(AppUser owner) throws Exception {
        StsRun run = runWithPlayerState("{\"currentHp\":70,\"maxHp\":70}");
        run.setOwner(owner);
        when(gameDataService.cards("2026-08-20.1")).thenReturn(objectMapper.readTree("""
                {"dataVersion":"2026-08-20.1","cards":[
                  {"id":"strike","cost":1,"targetingMode":"Enemy","tags":["SecretUnlock"],
                   "effects":[{"type":"Damage","value":6}]}
                ]}
                """));
        return run;
    }

    private StsRun runWithPlayerState(String playerState) throws Exception {
        StsRun run = new StsRun();
        ReflectionTestUtils.setField(run, "id", UUID.randomUUID());
        run.setDataVersion("2026-08-20.1");
        run.setPlayerState(objectMapper.readTree(playerState));
        run.setRunInventory(objectMapper.readTree("""
                {"deck":[{"instanceId":"card-1","cardId":"strike","enchantments":[]}],"relics":[]}
                """));
        run.setActiveEncounter(objectMapper.readTree("""
                {"encounterInstanceId":"enc-1","enemyIds":["slime"]}
                """));

        when(gameDataService.cards("2026-08-20.1")).thenReturn(objectMapper.readTree("""
                {"dataVersion":"2026-08-20.1","cards":[
                  {"id":"strike","cost":1,"targetingMode":"Enemy",
                   "effects":[{"type":"Damage","value":6}]}
                ]}
                """));
        when(gameDataService.enemies("2026-08-20.1")).thenReturn((com.fasterxml.jackson.databind.node.ArrayNode)
                objectMapper.readTree("""
                        [{"id":"slime","maxHP":20,
                          "movePattern":[{"effects":[{"type":"Damage","value":3}],"nextMoveIndices":[0]}]}]
                        """));
        return run;
    }

    private static JsonNode playerCombatant(JsonNode state) {
        return combatant(state, "player");
    }

    private static JsonNode combatant(JsonNode state, String combatantId) {
        for (JsonNode combatant : state.path("combatants")) {
            if (combatantId.equals(combatant.path("combatantId").asText())) {
                return combatant;
            }
        }
        throw new AssertionError("combatant '" + combatantId + "' is missing from the state");
    }

    @Test
    void cachesRuntimeContextAtCreationAndReusesItAcrossCommands() throws Exception {
        StsRun run = runWithPlayerState("{\"currentHp\":70,\"maxHp\":70}");
        JsonNode combat = service.createCombatState(run);
        run.setActiveCombat(combat);
        UUID combatId = UUID.fromString(combat.path("combatId").asText());

        assertThat(service.isRuntimeContextCached(combatId)).isTrue();

        // Submitting command hits the cache
        StsCombatCommandResponse response = service.submitCommand(run, new StsCombatCommandRequest(
                "END_TURN", combat.path("revision").asLong(), null, List.of(), List.of()));

        assertThat(response.accepted()).isTrue();
        assertThat(service.isRuntimeContextCached(combatId)).isTrue();
    }

    @Test
    void lazilyRebuildsRuntimeContextIfAbsentFromCache() throws Exception {
        StsRun run = runWithPlayerState("{\"currentHp\":70,\"maxHp\":70}");
        JsonNode combat = service.createCombatState(run);
        run.setActiveCombat(combat);
        UUID combatId = UUID.fromString(combat.path("combatId").asText());

        // Simulate server restart or cache clear
        service.clearRuntimeContextCache();
        assertThat(service.isRuntimeContextCached(combatId)).isFalse();

        StsCombatCommandResponse response = service.submitCommand(run, new StsCombatCommandRequest(
                "END_TURN", combat.path("revision").asLong(), null, List.of(), List.of()));

        assertThat(response.accepted()).isTrue();
        assertThat(service.isRuntimeContextCached(combatId)).isTrue();
    }

    @Test
    void invalidatesRuntimeContextWhenCombatFinishes() throws Exception {
        StsRun run = runWithPlayerState("{\"currentHp\":70,\"maxHp\":70}");
        JsonNode combat = service.createCombatState(run);
        run.setActiveCombat(combat);
        UUID combatId = UUID.fromString(combat.path("combatId").asText());

        assertThat(service.isRuntimeContextCached(combatId)).isTrue();

        // When snapshot is taken at completion
        service.snapshotForCompletion(run);
        // Eviction happens when state is FINISHED
        // Let's also verify explicit eviction on finished combat
        service.evictRuntimeContext(combatId);
        assertThat(service.isRuntimeContextCached(combatId)).isFalse();
    }

    @Test
    void invalidatesRuntimeContextWhenCommandFinishesCombat() throws Exception {
        StsRun run = runWithPlayerState("{\"currentHp\":70,\"maxHp\":70}");
        // Setup enemy with 5 HP so Strike (6 dmg) kills it
        when(gameDataService.enemies("2026-08-20.1")).thenReturn((com.fasterxml.jackson.databind.node.ArrayNode)
                objectMapper.readTree("""
                        [{"id":"slime","maxHP":5,
                          "movePattern":[{"effects":[{"type":"Damage","value":3}],"nextMoveIndices":[0]}]}]
                        """));
        JsonNode combat = service.createCombatState(run);
        run.setActiveCombat(combat);
        UUID combatId = UUID.fromString(combat.path("combatId").asText());

        assertThat(service.isRuntimeContextCached(combatId)).isTrue();

        StsCombatCommandResponse response = service.submitCommand(run, new StsCombatCommandRequest(
                "PLAY_CARD", combat.path("revision").asLong(), "card-1", List.of("enemy-0"), List.of()));

        assertThat(response.accepted()).isTrue();
        // Evicted upon completion by submitCommand
        assertThat(service.isRuntimeContextCached(combatId)).isFalse();

        // If re-populated in cache and snapshot is taken at completion, it is also evicted
        service.createCombatState(run);
        assertThat(service.isRuntimeContextCached(combatId)).isTrue();
        var snapshot = service.snapshotForCompletion(run);
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.victory()).isTrue();
        assertThat(service.isRuntimeContextCached(combatId)).isFalse();
    }
}
