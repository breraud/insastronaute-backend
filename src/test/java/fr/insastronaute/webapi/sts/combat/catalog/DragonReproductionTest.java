package fr.insastronaute.webapi.sts.combat.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.sts.combat.ai.PatternEnemyPolicy;
import fr.insastronaute.webapi.sts.combat.command.CombatCommand;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Reproduction, verbatim, of the Dragon move the live server was given on 2026-08-23,
 * during a fight where the boss started its turn and ended it without acting.
 *
 * <p>Kept as a regression: it pins the whole path from authored move to issued command
 * with real production data, which is what told us the engine was not at fault when a
 * boss stood still for a whole fight.</p>
 */
class DragonReproductionTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EnemyMoveParser parser = new EnemyMoveParser();
    private final CardCatalog catalog = new CardCatalog("v1", Map.of());

    /** Move 0 of the Dragon, copied field for field from the game data release. */
    private static final String DRAGON_MOVE_0 = """
            {
              "cardId": null,
              "moveName": "",
              "effects": [
                {
                  "type": "Damage", "value": 3, "targetSelf": false, "targetOthers": false,
                  "statusType": "Strengthen", "duration": 0, "description": "", "cardID": "",
                  "conditional": false, "conditionType": "KillingBlow", "conditionValue": "",
                  "trueEffect": false, "cardSelectionSource": "Hand", "index": 0,
                  "cardFilterTags": [], "cardSelectionEffect": "None", "animationType": "Slash"
                },
                {
                  "type": "Status", "value": 2, "targetSelf": false, "targetOthers": false,
                  "statusType": "Burn", "duration": 5, "description": "", "cardID": "",
                  "conditional": false, "conditionType": "KillingBlow", "conditionValue": "",
                  "trueEffect": false, "cardSelectionSource": "Hand", "index": 0,
                  "cardFilterTags": [], "cardSelectionEffect": "None", "animationType": "Default"
                }
              ],
              "nextMoveIndices": [1, 2],
              "weight": 0
            }
            """;

    @Test
    void theDragonsFirstMoveParsesIntoAPlayableDefinition() throws Exception {
        JsonNode move = OBJECT_MAPPER.readTree(DRAGON_MOVE_0);

        CardDefinition definition = parser.parseMove(catalog, move, "Dragon", 0);

        assertThat(definition)
                .as("a null definition here is exactly what empties the pattern, "
                        + "which makes the boss end its turn without acting")
                .isNotNull();
        assertThat(definition.effects()).hasSize(2);
        assertThat(definition.cost())
                .as("cost must not exceed the 0 energy an enemy holds")
                .isZero();
        assertThat(definition.targetingMode())
                .as("both effects aim away from the caster")
                .isEqualTo(TargetingMode.SINGLE_ENEMY);
    }

    /**
     * The whole path, with the numbers the live fight actually had: an AI enemy holding
     * zero energy, the player alive on the other team, pattern index 0. If this returns
     * an end-of-turn, we have reproduced the bug.
     */
    @Test
    void theDragonPlaysItsMoveInsteadOfEndingItsTurn() throws Exception {
        CardDefinition move = parser.parseMove(
                catalog, OBJECT_MAPPER.readTree(DRAGON_MOVE_0), "Dragon", 0);

        var policy = new PatternEnemyPolicy(
                new CardCatalog("v1", Map.of(move.id(), move)),
                Map.of("enemy-0", List.of(
                        new PatternEnemyPolicy.EnemyMoveNode(move.id(), List.of(1, 2), 0))));

        CombatState state = new CombatState(
                UUID.fromString("00000000-0000-0000-0000-0000000000d1"),
                5, "2026.7.0", CombatStatus.ACTIVE, null, new RandomState(123L),
                List.of(
                        new CombatantState("player", "player", ControllerType.HUMAN,
                                80, 45, 0, 3, 3, 25,
                                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                                List.of(), List.of(), null, 0),
                        new CombatantState("enemy-0", "enemy", ControllerType.AI,
                                75, 75, 0, 0, 0, 25,
                                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                                List.of(), List.of(), null, 0)),
                List.of(new TimelineEntry("enemy-0", 0, 0), new TimelineEntry("player", 1, 1)),
                "enemy-0");

        CombatCommand command = policy.nextCommand(state);

        assertThat(command.getClass().getSimpleName())
                .as("an EndTurnCommand here is the silent turn the player watched")
                .isEqualTo("AiPlayPatternCommand");
    }
}
