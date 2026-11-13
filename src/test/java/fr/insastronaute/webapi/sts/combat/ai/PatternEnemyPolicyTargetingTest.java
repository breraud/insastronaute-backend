package fr.insastronaute.webapi.sts.combat.ai;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.AiPlayPatternCommand;
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
 * Who an enemy hits when its move is aimed at a whole side of the fight.
 *
 * <p>The policy answered "nobody" for {@code AllEnemies}, {@code AllCharacters} and
 * {@code RandomEnemy}, and an enemy with no target ends its turn: every enemy whose pattern
 * opens with a sweeping attack simply passed. Unity has an enemy aim at the living characters
 * opposite it ({@code TurnSystem.cs:231}).
 */
class PatternEnemyPolicyTargetingTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final String ENEMY = "enemy";
    private static final String HERO = "hero";
    private static final String ALLY = "ally";
    private static final String FALLEN = "fallen";

    @Test
    void sweepsEveryLivingCharacterOppositeIt() {
        CombatCommand command = decide(TargetingMode.ALL_ENEMIES);

        assertThat(command).isInstanceOf(AiPlayPatternCommand.class);
        assertThat(((AiPlayPatternCommand) command).targetIds())
                .containsExactly(HERO, ALLY);
    }

    @Test
    void countsItselfInWhenTheMoveReachesEveryone() {
        CombatCommand command = decide(TargetingMode.ALL_CHARACTERS);

        assertThat(((AiPlayPatternCommand) command).targetIds())
                .containsExactlyInAnyOrder(HERO, ALLY, ENEMY);
    }

    @Test
    void picksOneLivingCharacterForARandomTarget() {
        CombatCommand command = decide(TargetingMode.RANDOM_ENEMY);

        assertThat(((AiPlayPatternCommand) command).targetIds()).containsExactly(HERO);
    }

    @Test
    void anyPlayerFromAnEnemyAimsAtItsOwnSide() {
        CombatCommand command = decide(TargetingMode.ANY_PLAYER);

        assertThat(((AiPlayPatternCommand) command).targetIds()).containsExactly(ENEMY);
    }

        @Test
        void playsAMoveThatDoesNotRequireTargets() {
                CombatCommand command = decide(TargetingMode.NONE);

                assertThat(command).isEqualTo(new AiPlayPatternCommand(
                                COMBAT_ID, 7, ENEMY, "sweep", List.of(), 0));
        }

    private static CombatCommand decide(TargetingMode mode) {
        CardDefinition move = new CardDefinition("sweep", 0, mode,
                List.of(new EffectDefinition(EffectType.DAMAGE, 5, false, null, 0, "", null)));
        PatternEnemyPolicy policy = new PatternEnemyPolicy(
                new CardCatalog("v1", Map.of(move.id(), move)),
                Map.of(ENEMY, List.of(
                        new PatternEnemyPolicy.EnemyMoveNode("sweep", List.of(), 1))));
        return policy.nextCommand(state());
    }

    private static CombatState state() {
        return new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                List.of(
                        character(HERO, 30),
                        character(ALLY, 30),
                        character(FALLEN, 0),
                        enemy()),
                List.of(
                        new TimelineEntry(ENEMY, 0, 0),
                        new TimelineEntry(HERO, 100, 1),
                        new TimelineEntry(ALLY, 200, 2)),
                ENEMY);
    }

    private static CombatantState character(String combatantId, int hp) {
        return new CombatantState(
                combatantId,
                "players",
                ControllerType.HUMAN,
                30,
                hp,
                0,
                3,
                3,
                100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
    }

    private static CombatantState enemy() {
        return new CombatantState(
                ENEMY,
                "enemies",
                ControllerType.AI,
                20,
                20,
                0,
                3,
                3,
                100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
    }
}
