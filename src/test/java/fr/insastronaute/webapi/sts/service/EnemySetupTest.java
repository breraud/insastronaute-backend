package fr.insastronaute.webapi.sts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import fr.insastronaute.webapi.sts.combat.api.CombatCommandMetrics;
import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentEngine;
import fr.insastronaute.webapi.sts.domain.StsRun;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * What an enemy walks into a combat carrying.
 *
 * <p>Its starting status and where it opens in its move pattern are authored data the engine used
 * to ignore entirely, so every enemy started plain and on its first move.
 */
class EnemySetupTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final StsGameDataService gameDataService = mock(StsGameDataService.class);
    private final StsAuthoritativeCombatService service = new StsAuthoritativeCombatService(
            gameDataService, objectMapper, List.of(), new EnchantmentEngine(List.of()),
            new CombatCommandMetrics(new SimpleMeterRegistry()));

    @Test
    void appliesTheStatusAnEnemyStartsWith() throws Exception {
        JsonNode state = createCombat("""
                [{"id":"astrum","maxHP":40,"randomStart":false,
                  "startingStatus":"Regulator","startingStatusValue":25,
                  "startingStatusDuration":0,"startingStatusInfo":"",
                  "movePattern":[{"effects":[{"type":"Damage","value":5}],"nextMoveIndices":[0]}]}]
                """);

        assertThat(enemy(state).path("statuses")).hasSize(1);
        assertThat(enemy(state).path("statuses").get(0).path("statusType").asText())
                .isEqualTo("Regulator");
        assertThat(enemy(state).path("statuses").get(0).path("value").asInt()).isEqualTo(25);
    }

    @Test
    void leavesAnEmptyStatusSlotAlone() throws Exception {
        JsonNode state = createCombat("""
                [{"id":"astrum","maxHP":40,"randomStart":false,
                  "startingStatus":"Strength","startingStatusValue":0,
                  "startingStatusDuration":0,"startingStatusInfo":"",
                  "movePattern":[{"effects":[{"type":"Damage","value":5}],"nextMoveIndices":[0]}]}]
                """);

        assertThat(enemy(state).path("statuses"))
                .describedAs("a status with neither a value nor a duration is an empty slot")
                .isEmpty();
    }

    @Test
    void scalesEnemyHealthByTheActAndTheChosenCharacter() throws Exception {
        when(gameDataService.enemyPool("v1")).thenReturn(objectMapper.readTree("""
                {"baseHpScaling":1.2,"actHpScaling":[0.5,1.2,2.0,3.0]}
                """));
        when(gameDataService.character("v1", "CFI")).thenReturn(objectMapper.readTree("""
                {"id":"CFI","hpAdditionalMultiplier":0.3}
                """));

        JsonNode state = createCombat("""
                [{"id":"astrum","maxHP":40,"randomStart":false,
                  "movePattern":[{"effects":[{"type":"Damage","value":5}],"nextMoveIndices":[0]}]}]
                """);

        assertThat(enemy(state).path("maxHp").asInt())
                .describedAs("40 health, scaled by 1.2 base plus 0.5 for the act plus 0.3 for the character")
                .isEqualTo(80);
        assertThat(enemy(state).path("hp").asInt())
                .describedAs("and it walks in at full health")
                .isEqualTo(80);
    }

    @Test
    void leavesHealthAloneWhenThereIsNoScalingTableToRead() throws Exception {
        JsonNode state = createCombat("""
                [{"id":"astrum","maxHP":40,"randomStart":false,
                  "movePattern":[{"effects":[{"type":"Damage","value":5}],"nextMoveIndices":[0]}]}]
                """);

        assertThat(enemy(state).path("maxHp").asInt())
                .describedAs("a missing table must not fail the combat nor invent a multiplier")
                .isEqualTo(40);
    }

    @Test
    void opensOnItsFirstMoveUnlessItStartsAtRandom() throws Exception {
        JsonNode fixed = createCombat("""
                [{"id":"astrum","maxHP":40,"randomStart":false,"movePattern":[
                  {"effects":[{"type":"Damage","value":5}],"weight":1,"nextMoveIndices":[1]},
                  {"effects":[{"type":"Armor","value":5,"targetSelf":true}],"weight":1,"nextMoveIndices":[0]}]}]
                """);

        assertThat(enemy(fixed).path("patternIndex").asInt()).isZero();
    }

    @Test
    void picksTheSameOpeningMoveEveryTimeTheSameEncounterIsBuilt() throws Exception {
        String enemies = """
                [{"id":"astrum","maxHP":40,"randomStart":true,"movePattern":[
                  {"effects":[{"type":"Damage","value":5}],"weight":1,"nextMoveIndices":[1]},
                  {"effects":[{"type":"Armor","value":5,"targetSelf":true}],"weight":1,"nextMoveIndices":[2]},
                  {"effects":[{"type":"Heal","value":3,"targetSelf":true}],"weight":1,"nextMoveIndices":[0]}]}]
                """;
        UUID runId = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

        int first = enemy(createCombat(enemies, runId)).path("patternIndex").asInt();
        int second = enemy(createCombat(enemies, runId)).path("patternIndex").asInt();

        assertThat(first).isEqualTo(second);
        assertThat(first).isBetween(0, 2);
    }

    private JsonNode createCombat(String enemies) throws Exception {
        return createCombat(enemies, UUID.randomUUID());
    }

    private JsonNode createCombat(String enemies, UUID runId) throws Exception {
        StsRun run = new StsRun();
        ReflectionTestUtils.setField(run, "id", runId);
        run.setDataVersion("v1");
        run.setSelectedCharacter("CFI");
        run.setPlayerState(objectMapper.readTree("{\"currentHp\":70,\"maxHp\":70}"));
        run.setRunInventory(objectMapper.readTree("""
                {"deck":[{"instanceId":"card-1","cardId":"strike","enchantments":[]}],"relics":[]}
                """));
        run.setActiveEncounter(objectMapper.readTree("""
                {"encounterInstanceId":"enc-1","enemyIds":["astrum"]}
                """));

        when(gameDataService.cards("v1")).thenReturn(objectMapper.readTree("""
                {"dataVersion":"v1","cards":[
                  {"id":"strike","cost":1,"targetingMode":"Enemy","effects":[{"type":"Damage","value":6}]}
                ]}
                """));
        when(gameDataService.enemies("v1"))
                .thenReturn((ArrayNode) objectMapper.readTree(enemies));

        return service.createCombatState(run);
    }

    private static JsonNode enemy(JsonNode state) {
        for (JsonNode combatant : state.path("combatants")) {
            if (combatant.path("combatantId").asText().startsWith("enemy")) {
                return combatant;
            }
        }
        throw new IllegalStateException("no enemy in " + state);
    }
}
