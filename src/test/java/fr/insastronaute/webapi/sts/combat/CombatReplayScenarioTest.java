package fr.insastronaute.webapi.sts.combat;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.ai.SingleCardEnemyPolicy;
import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.engine.CombatEngine;
import fr.insastronaute.webapi.sts.combat.engine.CombatFactory;
import fr.insastronaute.webapi.sts.combat.engine.CombatOrchestrator;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatCreated;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.EPRelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.GCRelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.MECARelicHandler;
import fr.insastronaute.webapi.sts.combat.engine.relic.handlers.MRIERelicHandler;
import fr.insastronaute.webapi.sts.combat.model.CombatSetup;
import fr.insastronaute.webapi.sts.combat.event.ArmorGained;
import fr.insastronaute.webapi.sts.combat.event.CardDrawn;
import fr.insastronaute.webapi.sts.combat.event.CardMoved;
import fr.insastronaute.webapi.sts.combat.event.CardPlayed;
import fr.insastronaute.webapi.sts.combat.event.CombatEnded;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.DamageApplied;
import fr.insastronaute.webapi.sts.combat.event.EnergyGained;
import fr.insastronaute.webapi.sts.combat.event.EnergySpent;
import fr.insastronaute.webapi.sts.combat.event.PileShuffled;
import fr.insastronaute.webapi.sts.combat.event.TurnEnded;
import fr.insastronaute.webapi.sts.combat.event.TurnStarted;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CombatReplayScenarioTest {

    @Test
    void replaysACompletePlayerVictoryWithAnIdenticalStateAndEventTrace() {
        CardCatalog catalog = CombatFixtures.catalog();
        CombatSetup setup = CombatFixtures.setup();

        ScenarioRun first = runScenario(setup, catalog);
        ScenarioRun replay = runScenario(setup, catalog);

        assertThat(replay.finalState()).isEqualTo(first.finalState());
        assertThat(replay.events()).containsExactlyElementsOf(first.events());

        CombatState finalState = first.finalState();
        assertThat(finalState.status()).isEqualTo(CombatStatus.FINISHED);
        assertThat(finalState.winnerTeamId()).isEqualTo(CombatFixtures.PLAYER_TEAM_ID);
        assertThat(finalState.activeCombatantId()).isNull();
        assertThat(combatant(finalState, CombatFixtures.ENEMY_ID).hp()).isZero();
        assertThat(combatant(finalState, CombatFixtures.PLAYER_ID).hp()).isEqualTo(20);
        assertThat(combatant(finalState, CombatFixtures.PLAYER_ID).armor()).isEqualTo(0);

        assertThat(first.events())
                .anyMatch(DamageApplied.class::isInstance)
                .anyMatch(ArmorGained.class::isInstance)
                .anyMatch(CardDrawn.class::isInstance)
                .anyMatch(EnergyGained.class::isInstance);
        assertThat(eventTypes(first.events())).containsExactly(
                "PileShuffled",
                "CardDrawn",
                "CardDrawn",
                "CardDrawn",
                "CardDrawn",
                "CardDrawn",
                "TurnStarted",
                "CardPlayed",
                "EnergySpent",
                "EnergyGained",
                "CardPlayed",
                "EnergySpent",
                "ArmorGained",
                "CardPlayed",
                "EnergySpent",
                "PileShuffled",
                "CardDrawn",
                "CardPlayed",
                "EnergySpent",
                "DamageApplied",
                "CardMoved",
                "CardMoved",
                "TurnEnded",
                "CardDrawn",
                "TurnStarted",
                "CardPlayed",
                "EnergySpent",
                "DamageApplied",
                "TurnEnded",
                "CardDrawn",
                "PileShuffled",
                "CardDrawn",
                "CardDrawn",
                "CardDrawn",
                "CardDrawn",
                "TurnStarted",
                "CardPlayed",
                "EnergySpent",
                "DamageApplied",
                "CombatEnded");
        assertThat(first.events().getLast()).isInstanceOf(CombatEnded.class);

        assertThat(first.operationRevisions())
                .containsExactly(0L, 1L, 2L, 3L, 4L, 5L, 7L, 8L);
        assertThat(first.events().stream()
                        .map(CombatEvent::revision)
                        .filter(revision -> revision > 0)
                        .distinct()
                        .toList())
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);

        assertCardsConserved(
                finalState,
                CombatFixtures.PLAYER_ID,
                CombatFixtures.playerDeck());
        assertCardsConserved(
                finalState,
                CombatFixtures.ENEMY_ID,
                CombatFixtures.enemyDeck());
    }

    private static ScenarioRun runScenario(CombatSetup setup, CardCatalog catalog) {
        RelicEngine relicEngine = new RelicEngine(List.of(new EPRelicHandler(), new MECARelicHandler(), new GCRelicHandler(), new MRIERelicHandler()));
        CombatEngine engine = new CombatEngine(catalog, relicEngine);
        CombatOrchestrator orchestrator =
                new CombatOrchestrator(engine, new SingleCardEnemyPolicy(catalog));
        CombatCreated created = new CombatFactory(catalog, relicEngine).create(setup);
        CombatState state = created.state();
        List<CombatEvent> events = new ArrayList<>(created.events());
        List<Long> operationRevisions = new ArrayList<>();
        operationRevisions.add(state.revision());

        state = play(
                engine,
                state,
                CombatFixtures.ENERGIZE,
                CombatFixtures.PLAYER_ID,
                events,
                operationRevisions);
        state = play(
                engine,
                state,
                CombatFixtures.FORTIFY,
                CombatFixtures.PLAYER_ID,
                events,
                operationRevisions);
        state = play(
                engine,
                state,
                CombatFixtures.INSIGHT,
                CombatFixtures.PLAYER_ID,
                events,
                operationRevisions);
        state = play(
                engine,
                state,
                CombatFixtures.JAB,
                CombatFixtures.ENEMY_ID,
                events,
                operationRevisions);

        CombatResult endTurn = engine.handle(state, new EndTurnCommand(
                state.combatId(), state.revision(), CombatFixtures.PLAYER_ID));
        state = accepted(endTurn, events, operationRevisions);

        CombatOrchestrator.Advance advance = orchestrator.advanceAi(state);
        assertThat(advance.state().revision()).isGreaterThan(state.revision());
        events.addAll(advance.events());
        state = advance.state();
        operationRevisions.add(state.revision());

        state = play(
                engine,
                state,
                CombatFixtures.FINISHER,
                CombatFixtures.ENEMY_ID,
                events,
                operationRevisions);
        return new ScenarioRun(state, events, operationRevisions);
    }

    private static CombatState play(
            CombatEngine engine,
            CombatState state,
            String definitionId,
            String targetId,
            List<CombatEvent> events,
            List<Long> operationRevisions
    ) {
        CardInstance card = combatant(state, CombatFixtures.PLAYER_ID)
                .piles()
                .hand()
                .stream()
                .filter(candidate -> candidate.definitionId().equals(definitionId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected card in player hand: " + definitionId));
        CombatResult result = engine.handle(state, new PlayCardCommand(
                state.combatId(),
                state.revision(),
                CombatFixtures.PLAYER_ID,
                card.instanceId(),
                List.of(targetId)));
        return accepted(result, events, operationRevisions);
    }

    private static CombatState accepted(
            CombatResult result,
            List<CombatEvent> events,
            List<Long> operationRevisions
    ) {
        assertThat(result.accepted())
                .as("combat command accepted, rejection=%s", result.rejection())
                .isTrue();
        events.addAll(result.events());
        operationRevisions.add(result.state().revision());
        return result.state();
    }

    private static void assertCardsConserved(
            CombatState state,
            String ownerId,
            List<CardInstance> initialDeck
    ) {
        List<CardInstance> finalCards = allCards(combatant(state, ownerId).piles());

        assertThat(finalCards).containsExactlyInAnyOrderElementsOf(initialDeck);
        assertThat(finalCards)
                .extracting(CardInstance::instanceId)
                .doesNotHaveDuplicates();
    }

    private static List<CardInstance> allCards(CardPiles piles) {
        return List.of(piles.draw(), piles.hand(), piles.discard(), piles.exhaust())
                .stream()
                .flatMap(List::stream)
                .toList();
    }

    private static CombatantState combatant(CombatState state, String combatantId) {
        return state.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> eventTypes(List<CombatEvent> events) {
        return events.stream()
                .map(event -> event.getClass().getSimpleName())
                .toList();
    }

    private record ScenarioRun(
            CombatState finalState,
            List<CombatEvent> events,
            List<Long> operationRevisions
    ) {

        private ScenarioRun {
            events = List.copyOf(events);
            operationRevisions = List.copyOf(operationRevisions);
        }
    }
}
