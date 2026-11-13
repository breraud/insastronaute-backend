package fr.insastronaute.webapi.sts.combat.ai;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.AiPlayPatternCommand;
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
 * Which move an enemy plays next.
 *
 * <p>Unity walks a graph: a move names the moves that may follow it, each weighted, and a move
 * that names none simply wraps around ({@code EnemyData.GetNextActionIndex}). The engine added
 * one to the index and never wrapped, so an enemy played its pattern once and then repeated its
 * first move for the rest of the fight — and every enemy authored as a graph, which is most of
 * the roster, fought in the wrong order from its second move on.
 */
class PatternTransitionTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final String ENEMY = "enemy";
    private static final String HERO = "hero";

    @Test
    void followsTheSuccessorAMoveNames() {
        int next = decide(0,
                node("a", List.of(2), 1),
                node("b", List.of(), 1),
                node("c", List.of(), 1));

        assertThat(next).isEqualTo(2);
    }

    @Test
    void wrapsAroundWhenAMoveNamesNoSuccessor() {
        int next = decide(2,
                node("a", List.of(), 1),
                node("b", List.of(), 1),
                node("c", List.of(), 1));

        assertThat(next).isZero();
    }

    @Test
    void leansOnTheWeightOfTheSuccessors() {
        int next = decide(0,
                node("a", List.of(1, 2), 1),
                node("b", List.of(), 1),
                node("c", List.of(), 1000));

        assertThat(next).isEqualTo(2);
    }

    @Test
    void putsTheEnemyOnTheMoveTheGraphChoseAndMovesTheStreamOn() {
        CardDefinition move = new CardDefinition("a", 0, TargetingMode.SINGLE_ENEMY,
                List.of(new EffectDefinition(EffectType.DAMAGE, 5, false, null, 0, "", null)));
        CombatState state = state(0);
        fr.insastronaute.webapi.sts.combat.engine.CombatEngine engine =
                new fr.insastronaute.webapi.sts.combat.engine.CombatEngine(
                        new CardCatalog("v1", Map.of(move.id(), move)),
                        new fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine(List.of()));

        var result = engine.handle(state, new AiPlayPatternCommand(
                COMBAT_ID, state.revision(), ENEMY, "a", List.of(HERO), 2));

        assertThat(result.accepted()).isTrue();
        assertThat(result.state().combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(ENEMY))
                .findFirst()
                .orElseThrow()
                .patternIndex()).isEqualTo(2);
        assertThat(result.state().random()).isNotEqualTo(state.random());
    }

    private static int decide(int currentIndex, PatternEnemyPolicy.EnemyMoveNode... pattern) {
        CardDefinition move = new CardDefinition("a", 0, TargetingMode.SINGLE_ENEMY,
                List.of(new EffectDefinition(EffectType.DAMAGE, 5, false, null, 0, "", null)));
        Map<String, CardDefinition> catalog = new java.util.HashMap<>();
        for (PatternEnemyPolicy.EnemyMoveNode node : pattern) {
            catalog.put(node.cardId(), new CardDefinition(node.cardId(), 0,
                    TargetingMode.SINGLE_ENEMY, move.effects()));
        }
        PatternEnemyPolicy policy = new PatternEnemyPolicy(
                new CardCatalog("v1", catalog),
                Map.of(ENEMY, List.of(pattern)));
        AiPlayPatternCommand command =
                (AiPlayPatternCommand) policy.nextCommand(state(currentIndex));
        return command.nextPatternIndex();
    }

    private static PatternEnemyPolicy.EnemyMoveNode node(
            String cardId, List<Integer> nextIndices, int weight) {
        return new PatternEnemyPolicy.EnemyMoveNode(cardId, nextIndices, weight);
    }

    private static CombatState state(int patternIndex) {
        CombatantState enemy = new CombatantState(
                ENEMY,
                "enemies",
                ControllerType.AI,
                20,
                20,
                0,
                3,
                3,
                100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()),
                List.of(),
                List.of(),
                null,
                patternIndex);
        CombatantState hero = new CombatantState(
                HERO,
                "players",
                ControllerType.HUMAN,
                30,
                30,
                0,
                3,
                3,
                100,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
        return new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(2026),
                List.of(enemy, hero),
                List.of(new TimelineEntry(ENEMY, 0, 0), new TimelineEntry(HERO, 100, 1)),
                ENEMY);
    }
}
