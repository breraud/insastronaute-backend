package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import fr.insastronaute.webapi.sts.combat.ai.EnemyPolicy;
import fr.insastronaute.webapi.sts.combat.ai.SingleCardEnemyPolicy;
import fr.insastronaute.webapi.sts.combat.ai.PatternEnemyPolicy;
import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.event.CombatEnded;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.TurnEnded;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatOrchestratorTest {

    private final RelicEngine relicEngine = new RelicEngine(List.of());
    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000082");
    private static final CardInstance STRIKE_ONE = new CardInstance("strike-1", "strike");
    private static final CardInstance STRIKE_TWO = new CardInstance("strike-2", "strike");

    private final CardCatalog catalog = new CardCatalog("v1", Map.of(
            "strike", new CardDefinition(
                    "strike",
                    1,
                    TargetingMode.SINGLE_ENEMY,
                    List.of(new EffectDefinition(EffectType.DAMAGE, 2))),
            "lethal", new CardDefinition(
                    "lethal",
                    1,
                    TargetingMode.SINGLE_ENEMY,
                    List.of(new EffectDefinition(EffectType.DAMAGE, 50)))));

    @Test
    void exposesTheConfigurableCommandLimitConstructorAsPublicApi() throws Exception {
        int modifiers = CombatOrchestrator.class
                .getDeclaredConstructor(CombatEngine.class, EnemyPolicy.class, int.class)
                .getModifiers();

        assertThat(Modifier.isPublic(modifiers)).isTrue();
    }

    @Test
    void playsSuccessiveCardsThenEndsTurnUntilAHumanBecomesActive() {
        CombatState initial = state(
                ControllerType.HUMAN,
                20,
                List.of(STRIKE_ONE, STRIKE_TWO),
                2);
        CombatEngine engine = new CombatEngine(catalog, relicEngine);
        CombatOrchestrator orchestrator =
                new CombatOrchestrator(engine, new SingleCardEnemyPolicy(catalog));

        CombatOrchestrator.Advance advance = orchestrator.advanceAi(initial);

        CombatResult first = engine.handle(initial, new PlayCardCommand(
                COMBAT_ID, 5, "enemy", "strike-1", List.of("hero")));
        CombatResult second = engine.handle(first.state(), new PlayCardCommand(
                COMBAT_ID, 6, "enemy", "strike-2", List.of("hero")));
        CombatResult third = engine.handle(second.state(), new EndTurnCommand(
                COMBAT_ID, 7, "enemy"));
        List<CombatEvent> expectedEvents = new ArrayList<>(first.events());
        expectedEvents.addAll(second.events());
        expectedEvents.addAll(third.events());

        assertThat(advance.state()).isEqualTo(third.state());
        assertThat(advance.events()).containsExactlyElementsOf(expectedEvents);
        assertThat(advance.state().revision()).isEqualTo(initial.revision() + 3);
        assertThat(advance.state().activeCombatantId()).isEqualTo("hero");
        assertThat(combatant(advance.state(), "hero").hp()).isEqualTo(16);
        assertThat(combatant(advance.state(), "enemy").piles().discard())
                .containsExactly(STRIKE_ONE, STRIKE_TWO);
        assertThat(advance.events()).anyMatch(TurnEnded.class::isInstance);
    }

    @Test
    void carriesTheEnemyPatternPositionAcrossItsOwnTurnEnd() {
        CardCatalog patternCatalog = new CardCatalog("v1", Map.of(
                "jab", new CardDefinition("jab", 0, TargetingMode.SINGLE_ENEMY,
                        List.of(new EffectDefinition(EffectType.DAMAGE, 1))),
                "slam", new CardDefinition("slam", 0, TargetingMode.SINGLE_ENEMY,
                        List.of(new EffectDefinition(EffectType.DAMAGE, 5)))));
        PatternEnemyPolicy policy = new PatternEnemyPolicy(patternCatalog, Map.of("enemy", List.of(
                new PatternEnemyPolicy.EnemyMoveNode("jab", List.of(1), 1),
                new PatternEnemyPolicy.EnemyMoveNode("slam", List.of(0), 1))));
        CombatOrchestrator orchestrator = new CombatOrchestrator(
                new CombatEngine(patternCatalog, relicEngine), policy);

        CombatState afterFirstAiTurn = orchestrator
                .advanceAi(state(ControllerType.HUMAN, 20, List.of(), 3))
                .state();

        assertThat(combatant(afterFirstAiTurn, "enemy").patternIndex())
                .describedAs("the enemy must resume at its next move, not restart its pattern")
                .isEqualTo(1);
        assertThat(combatant(afterFirstAiTurn, "hero").hp()).isEqualTo(19);
    }

    @Test
    void keepsTheAnnouncedIntentWhenTheHeroEndsItsTurn() {
        CombatState withIntent = withActiveHuman(state(ControllerType.HUMAN, 20, List.of(), 3));
        CombatantState enemy = combatant(withIntent, "enemy").withIntentCardId("strike");
        CombatState state = new CombatState(
                withIntent.combatId(), withIntent.revision(), withIntent.dataVersion(),
                withIntent.status(), withIntent.winnerTeamId(), withIntent.random(),
                withIntent.combatants().stream()
                        .map(combatant -> combatant.combatantId().equals("enemy") ? enemy : combatant)
                        .toList(),
                withIntent.timeline(), withIntent.activeCombatantId());

        CombatResult result = new CombatEngine(catalog, relicEngine)
                .handle(state, new EndTurnCommand(COMBAT_ID, state.revision(), "hero"));

        assertThat(result.accepted()).isTrue();
        assertThat(combatant(result.state(), "enemy").intentCardId()).isEqualTo("strike");
    }

    @Test
    void stopsOnAPlayerKillingCardWithoutEndingTheAiTurn() {
        CardInstance lethal = new CardInstance("lethal-1", "lethal");
        CombatState initial = state(ControllerType.HUMAN, 10, List.of(lethal), 1);
        CombatOrchestrator orchestrator = new CombatOrchestrator(
                new CombatEngine(catalog, relicEngine), new SingleCardEnemyPolicy(catalog));

        CombatOrchestrator.Advance advance = orchestrator.advanceAi(initial);

        assertThat(advance.state().status()).isEqualTo(CombatStatus.FINISHED);
        assertThat(advance.state().winnerTeamId()).isEqualTo("enemies");
        assertThat(advance.state().revision()).isEqualTo(initial.revision() + 1);
        assertThat(advance.events().getLast()).isInstanceOf(CombatEnded.class);
        assertThat(advance.events()).noneMatch(TurnEnded.class::isInstance);
    }

    @Test
    void returnsHumanOrFinishedInputsUnchangedWithoutEvents() {
        CombatOrchestrator orchestrator = new CombatOrchestrator(
                new CombatEngine(catalog, relicEngine), new SingleCardEnemyPolicy(catalog));
        CombatState human = withActiveHuman(state(
                ControllerType.HUMAN, 20, List.of(STRIKE_ONE), 1));
        List<CombatantState> finishedCombatants = human.combatants().stream()
                .map(combatant -> combatant.combatantId().equals("enemy")
                        ? combatant(
                                combatant.combatantId(),
                                combatant.teamId(),
                                combatant.controllerType(),
                                0,
                                combatant.energy(),
                                combatant.piles().hand())
                        : combatant)
                .toList();
        CombatState finished = new CombatState(
                human.combatId(),
                human.revision(),
                human.dataVersion(),
                CombatStatus.FINISHED,
                "players",
                human.random(),
                finishedCombatants,
                human.timeline(),
                null);

        CombatOrchestrator.Advance humanAdvance = orchestrator.advanceAi(human);
        CombatOrchestrator.Advance finishedAdvance = orchestrator.advanceAi(finished);

        assertThat(humanAdvance.state().revision()).isEqualTo(human.revision());
        assertThat(humanAdvance.state().status()).isEqualTo(human.status());
        assertThat(humanAdvance.events()).isEmpty();
        assertThat(finishedAdvance.state().revision()).isEqualTo(finished.revision());
        assertThat(finishedAdvance.events()).isEmpty();
    }

    @Test
    void failsFastWhenThePolicyProducesARejectedCommand() {
        CombatState initial = state(
                ControllerType.HUMAN, 20, List.of(STRIKE_ONE), 1);
        EnemyPolicy broken = state -> new EndTurnCommand(
                state.combatId(), state.revision(), "hero");
        CombatOrchestrator orchestrator =
                new CombatOrchestrator(new CombatEngine(catalog, relicEngine), broken);

        assertThatIllegalStateException()
                .isThrownBy(() -> orchestrator.advanceAi(initial))
                .withMessageContaining("rejected");
    }

    @Test
    void guardsAgainstAnAiOnlyCommandLoop() {
        CombatState aiOnly = state(
                ControllerType.AI, 20, List.of(), 0);
        EnemyPolicy alwaysEndTurn = state -> new EndTurnCommand(
                state.combatId(), state.revision(), state.activeCombatantId());
        CombatOrchestrator orchestrator =
                new CombatOrchestrator(new CombatEngine(catalog, relicEngine), alwaysEndTurn, 3);

        assertThatIllegalStateException()
                .isThrownBy(() -> orchestrator.advanceAi(aiOnly))
                .withMessageContaining("limit");
    }

    @Test
    void endsAnAiTurnAfterOnePatternAction() {
        CombatState initial = state(ControllerType.HUMAN, 20, List.of(), 3);
        CombatOrchestrator orchestrator = new CombatOrchestrator(
                new CombatEngine(catalog, relicEngine),
                new PatternEnemyPolicy(catalog, Map.of(
                        "enemy", List.of(new PatternEnemyPolicy.EnemyMoveNode("strike", List.of(0), 1)))),
                3);

        CombatOrchestrator.Advance advance = orchestrator.advanceAi(initial);

        assertThat(advance.state().activeCombatantId()).isEqualTo("hero");
        assertThat(advance.events()).anyMatch(TurnEnded.class::isInstance);
    }

    @Test
    void producesEqualAdvancesForEqualInputsAndValidatesConstruction() {
        CombatState initial = state(
                ControllerType.HUMAN,
                20,
                List.of(STRIKE_ONE, STRIKE_TWO),
                2);
        CombatOrchestrator orchestrator = new CombatOrchestrator(
                new CombatEngine(catalog, relicEngine), new SingleCardEnemyPolicy(catalog));

        assertThat(orchestrator.advanceAi(initial))
                .isEqualTo(orchestrator.advanceAi(initial));
        assertThatNullPointerException().isThrownBy(
                () -> new CombatOrchestrator(null, new SingleCardEnemyPolicy(catalog)));
        assertThatNullPointerException().isThrownBy(
                () -> new CombatOrchestrator(new CombatEngine(catalog, relicEngine), null));
        assertThatNullPointerException().isThrownBy(() -> orchestrator.advanceAi(null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new CombatOrchestrator(
                        new CombatEngine(catalog, relicEngine),
                        new SingleCardEnemyPolicy(catalog),
                        0));

        List<CombatEvent> mutableEvents = new ArrayList<>();
        CombatOrchestrator.Advance copied =
                new CombatOrchestrator.Advance(initial, mutableEvents);
        mutableEvents.addAll(orchestrator.advanceAi(initial).events());
        assertThat(copied.events()).isEmpty();
    }

    private static CombatState state(
            ControllerType heroController,
            int heroHp,
            List<CardInstance> enemyHand,
            int enemyEnergy
    ) {
        CombatantState enemy = combatant(
                "enemy", "enemies", ControllerType.AI, 20, enemyEnergy, enemyHand);
        CombatantState hero = combatant(
                "hero", "players", heroController, heroHp, 3, List.of());
        return new CombatState(
                COMBAT_ID,
                5,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(123),
                List.of(enemy, hero),
                List.of(
                        new TimelineEntry("enemy", 0, 0),
                        new TimelineEntry("hero", 1, 1)),
                "enemy");
    }

    private static CombatState withActiveHuman(CombatState state) {
        return new CombatState(
                state.combatId(),
                state.revision(),
                state.dataVersion(),
                state.status(),
                state.winnerTeamId(),
                state.random(),
                state.combatants(),
                List.of(
                        new TimelineEntry("hero", 0, 0),
                        new TimelineEntry("enemy", 1, 1)),
                "hero");
    }

    private static CombatantState combatant(
            String id,
            String team,
            ControllerType controller,
            int hp,
            int energy,
            List<CardInstance> hand
    ) {
        return new CombatantState(
                id,
                team,
                controller,
                20,
                hp,
                0,
                energy,
                3,
                10,
                new CardPiles(List.of(), hand, List.of(), List.of()));
    }

    private static CombatantState combatant(CombatState state, String id) {
        return state.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
