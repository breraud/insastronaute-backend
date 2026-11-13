package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.event.CardDrawn;
import fr.insastronaute.webapi.sts.combat.event.CardMoved;
import fr.insastronaute.webapi.sts.combat.event.PileShuffled;
import fr.insastronaute.webapi.sts.combat.event.TurnEnded;
import fr.insastronaute.webapi.sts.combat.event.TurnStarted;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatEngineTurnTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000071");
    private static final RandomState RANDOM = new RandomState(42);
    private final CombatEngine engine = new CombatEngine(new CardCatalog("v1", Map.of()), new RelicEngine(List.of()));

    @Test
    void endsTurnDiscardsHandAdvancesTimelineAndStartsNextActor() {
        CardInstance h1 = card("h1");
        CardInstance h2 = card("h2");
        CardInstance h3 = card("h3");
        CardInstance oldDiscard = card("old-discard");
        CardInstance d1 = card("d1");
        CardInstance d2 = card("d2");
        CombatantState hero = combatant(
                "hero", "players", 20, 1, 3, 10,
                new CardPiles(List.of(), List.of(h1, h2, h3), List.of(oldDiscard), List.of()));
        CombatantState enemy = combatant(
                "enemy", "enemies", 20, 7, 2, 12,
                new CardPiles(List.of(d1, d2), List.of(), List.of(), List.of()));
        CombatState original = state(
                List.of(hero, enemy),
                List.of(new TimelineEntry("hero", 4, 3), new TimelineEntry("enemy", 7, 4)),
                "hero");

        CombatResult result = end(original, "hero");

        assertThat(result.accepted()).isTrue();
        assertThat(result.state().revision()).isEqualTo(8);
        assertThat(result.state().activeCombatantId()).isEqualTo("enemy");
        assertThat(result.state().timeline()).containsExactly(
                new TimelineEntry("enemy", 7, 4),
                new TimelineEntry("hero", 14, 5));
        assertThat(combatant(result.state(), "hero").piles().hand()).isEmpty();
        assertThat(combatant(result.state(), "hero").piles().discard())
                .containsExactly(oldDiscard, h1, h2, h3);
        assertThat(combatant(result.state(), "hero").energy()).isEqualTo(1);
        assertThat(combatant(result.state(), "enemy").energy()).isEqualTo(2);
        assertThat(combatant(result.state(), "enemy").piles().hand()).containsExactly(d1, d2);
        assertThat(result.events()).containsExactly(
                new CardMoved(COMBAT_ID, 8, "hero", "h1", "definition", "HAND", "DISCARD", 1),
                new CardMoved(COMBAT_ID, 8, "hero", "h2", "definition", "HAND", "DISCARD", 2),
                new CardMoved(COMBAT_ID, 8, "hero", "h3", "definition", "HAND", "DISCARD", 3),
                new TurnEnded(COMBAT_ID, 8, "hero", 4, 14),
                new CardDrawn(COMBAT_ID, 8, "enemy", "d1", "definition", 0),
                new CardDrawn(COMBAT_ID, 8, "enemy", "d2", "definition", 1),
                new TurnStarted(COMBAT_ID, 8, "enemy", 7, 2));
        assertThat(original).isEqualTo(state(
                List.of(hero, enemy),
                List.of(new TimelineEntry("hero", 4, 3), new TimelineEntry("enemy", 7, 4)),
                "hero"));
    }

    @Test
    void removesDeadEntriesAndUsesReadySequenceIdentifierOrderingDeterministically() {
        CombatantState hero = combatant(
                "hero", "players", 20, 0, 3, 10, emptyPiles());
        CombatantState zeta = combatant(
                "zeta", "enemies", 20, 0, 2, 12, emptyPiles());
        CombatantState alpha = combatant(
                "alpha", "enemies", 20, 0, 2, 12, emptyPiles());
        CombatantState dead = combatant(
                "dead", "players", 0, 9, 9, 1, emptyPiles());
        CombatState original = state(
                List.of(hero, zeta, alpha, dead),
                List.of(
                        new TimelineEntry("dead", 1, 5),
                        new TimelineEntry("hero", 2, 0),
                        new TimelineEntry("alpha", 8, 6),
                        new TimelineEntry("zeta", 8, 6)),
                "hero");

        CombatResult first = end(original, "hero");
        CombatResult repeated = end(original, "hero");

        assertThat(first).isEqualTo(repeated);
        assertThat(first.state().activeCombatantId()).isEqualTo("alpha");
        assertThat(first.state().timeline()).containsExactly(
                new TimelineEntry("alpha", 8, 6),
                new TimelineEntry("zeta", 8, 6),
                new TimelineEntry("hero", 12, 7));
        assertThat(combatant(first.state(), "dead").energy()).isEqualTo(9);
    }

    @Test
    void reshufflesAtBoundaryAndCanScheduleTheSameActorAgain() {
        CardInstance keptDiscard = card("kept-discard");
        CardInstance h1 = card("h1");
        CardInstance h2 = card("h2");
        CombatantState hero = combatant(
                "hero", "players", 20, 1, 3, 2,
                new CardPiles(List.of(), List.of(h1, h2), List.of(keptDiscard), List.of()));
        CombatantState enemy = combatant(
                "enemy", "enemies", 20, 6, 2, 12, emptyPiles());
        CombatState original = state(
                List.of(hero, enemy),
                List.of(new TimelineEntry("hero", 1, 2), new TimelineEntry("enemy", 10, 3)),
                "hero");
        List<CardInstance> recycled = List.of(keptDiscard, h1, h2);
        PileOperations.ShuffleResult expected = PileOperations.shuffle(recycled, RANDOM);

        CombatResult result = end(original, "hero");

        assertThat(result.state().activeCombatantId()).isEqualTo("hero");
        assertThat(result.state().timeline()).containsExactly(
                new TimelineEntry("hero", 3, 4),
                new TimelineEntry("enemy", 10, 3));
        assertThat(combatant(result.state(), "hero").energy()).isEqualTo(3);
        assertThat(combatant(result.state(), "hero").piles().hand())
                .containsExactlyElementsOf(expected.cards());
        assertThat(result.state().random()).isEqualTo(expected.random());
        assertThat(result.events()).containsExactly(
                new CardMoved(COMBAT_ID, 8, "hero", "h1", "definition", "HAND", "DISCARD", 1),
                new CardMoved(COMBAT_ID, 8, "hero", "h2", "definition", "HAND", "DISCARD", 2),
                new TurnEnded(COMBAT_ID, 8, "hero", 1, 3),
                new PileShuffled(
                        COMBAT_ID, 8, "hero",
                        "DRAW", expected.cards().stream().map(CardInstance::instanceId).toList()),
                new CardDrawn(COMBAT_ID, 8, "hero", expected.cards().get(0).instanceId(), "definition", 0),
                new CardDrawn(COMBAT_ID, 8, "hero", expected.cards().get(1).instanceId(), "definition", 1),
                new CardDrawn(COMBAT_ID, 8, "hero", expected.cards().get(2).instanceId(), "definition", 2),
                new TurnStarted(COMBAT_ID, 8, "hero", 3, 3));
    }

    @Test
    void interleavesDrawAndShuffleEventsWhenNextActorCrossesPileBoundary() {
        CardInstance first = card("draw-first");
        CardInstance recycledOne = card("recycled-one");
        CardInstance recycledTwo = card("recycled-two");
        CombatantState hero = combatant(
                "hero", "players", 20, 1, 3, 10, emptyPiles());
        CombatantState enemy = combatant(
                "enemy", "enemies", 20, 0, 2, 12,
                new CardPiles(
                        List.of(first),
                        List.of(),
                        List.of(recycledOne, recycledTwo),
                        List.of()));
        CombatState original = state(
                List.of(hero, enemy),
                List.of(new TimelineEntry("hero", 1, 0), new TimelineEntry("enemy", 2, 1)),
                "hero");
        PileOperations.ShuffleResult expectedShuffle =
                PileOperations.shuffle(List.of(recycledOne, recycledTwo), RANDOM);

        CombatResult result = end(original, "hero");

        CombatantState started = combatant(result.state(), "enemy");
        assertThat(started.piles().hand()).containsExactly(
                first, expectedShuffle.cards().get(0), expectedShuffle.cards().get(1));
        assertThat(started.piles().draw()).isEmpty();
        assertThat(started.piles().discard()).isEmpty();
        assertThat(result.state().random()).isEqualTo(expectedShuffle.random());
        assertThat(result.events()).containsExactly(
                new TurnEnded(COMBAT_ID, 8, "hero", 1, 11),
                new CardDrawn(COMBAT_ID, 8, "enemy", first.instanceId(), "definition", 0),
                new PileShuffled(
                        COMBAT_ID,
                        8,
                        "enemy",
                        "DRAW", expectedShuffle.cards().stream()
                                .map(CardInstance::instanceId)
                                .toList()),
                new CardDrawn(COMBAT_ID, 8, "enemy", expectedShuffle.cards().get(0).instanceId(), "definition", 1),
                new CardDrawn(COMBAT_ID, 8, "enemy", expectedShuffle.cards().get(1).instanceId(), "definition", 2),
                new TurnStarted(COMBAT_ID, 8, "enemy", 2, 2));
    }

    @Test
    void rejectsSnapshotsWhenActiveTimelineEntryIsAbsentOrDuplicated() {
        CombatantState hero = combatant(
                "hero", "players", 20, 0, 3, 10, emptyPiles());
        CombatantState enemy = combatant(
                "enemy", "enemies", 20, 0, 2, 12, emptyPiles());

        assertThatIllegalArgumentException().isThrownBy(() -> state(
                List.of(hero, enemy),
                List.of(new TimelineEntry("enemy", 2, 1)),
                "hero"));
        assertThatIllegalArgumentException().isThrownBy(() -> state(
                List.of(hero, enemy),
                List.of(
                        new TimelineEntry("hero", 1, 0),
                        new TimelineEntry("hero", 2, 1),
                        new TimelineEntry("enemy", 3, 2)),
                "hero"));
    }

    @Test
    void turnEndedRejectsMalformedComponents() {
        assertThatNullPointerException().isThrownBy(
                () -> new TurnEnded(null, 0, "hero", 0, 1));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new TurnEnded(COMBAT_ID, -1, "hero", 0, 1));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new TurnEnded(COMBAT_ID, 0, " ", 0, 1));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new TurnEnded(COMBAT_ID, 0, "hero", -1, 1));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new TurnEnded(COMBAT_ID, 0, "hero", 2, 1));
    }

    private CombatResult end(CombatState state, String actorId) {
        return engine.handle(
                state,
                new EndTurnCommand(state.combatId(), state.revision(), actorId));
    }

    private static CombatState state(
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            String activeId
    ) {
        return new CombatState(
                COMBAT_ID,
                7,
                "v1",
                CombatStatus.ACTIVE,
                null,
                RANDOM,
                combatants,
                timeline,
                activeId);
    }

    private static CombatantState combatant(
            String id,
            String team,
            int hp,
            int energy,
            int baseEnergy,
            int delay,
            CardPiles piles
    ) {
        return new CombatantState(
                id,
                team,
                ControllerType.HUMAN,
                30,
                hp,
                0,
                energy,
                baseEnergy,
                delay,
                piles);
    }

    private static CombatantState combatant(CombatState state, String id) {
        return state.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static CardPiles emptyPiles() {
        return new CardPiles(List.of(), List.of(), List.of(), List.of());
    }

    private static CardInstance card(String id) {
        return new CardInstance(id, "definition");
    }
}
