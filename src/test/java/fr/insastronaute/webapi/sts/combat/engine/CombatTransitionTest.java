package fr.insastronaute.webapi.sts.combat.engine;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.event.CombatEnded;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatCounters;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatTransitionTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000f1");

    // Deliberately distinct from `running()`'s own revision (1) and from whatever
    // `previous` carries, so a field silently reused from `previous` instead of threaded
    // from `transition`'s own parameters makes an assertion fail rather than pass by
    // coincidence.
    private static final String DATA_VERSION = "combat-data-v7";
    private static final long TRANSITION_REVISION = 2L;
    private static final RandomState TRANSITION_RANDOM = new RandomState(2L);
    // atTurnStart() bumps turnCount to 1, distinct from the previous state's default
    // CombatCounters.initial() (turnCount 0) baked into running() -- so this only equals
    // the resulting state's counters if `transition` actually threads its own `counters`
    // parameter through, not if it silently kept whatever `previous` was holding.
    private static final CombatCounters TRANSITION_COUNTERS =
            CombatCounters.initial().atTurnStart();

    private static CombatantState combatant(String id, String team, int hp) {
        return new CombatantState(id, team, ControllerType.HUMAN, 10, hp, 0, 3, 3, 25,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
    }

    private static CombatState running(List<CombatantState> combatants, String active) {
        return new CombatState(
                COMBAT_ID, 1, DATA_VERSION, CombatStatus.ACTIVE, null, new RandomState(1L),
                combatants,
                List.of(new TimelineEntry(combatants.get(0).combatantId(), 0, 0),
                        new TimelineEntry(combatants.get(1).combatantId(), 10, 1)),
                active);
    }

    @Test
    void keepsTheCombatRunningWhileTwoTeamsStand() {
        List<CombatantState> combatants =
                List.of(combatant("hero", "player", 5), combatant("enemy-0", "enemy", 3));
        List<CombatEvent> events = new ArrayList<>();

        CombatResult result = CombatEngine.transition(
                running(combatants, "hero"), TRANSITION_REVISION, TRANSITION_RANDOM, combatants,
                List.of(new TimelineEntry("hero", 0, 0), new TimelineEntry("enemy-0", 10, 1)),
                "hero", events, TRANSITION_COUNTERS);

        assertThat(result.state().status()).isEqualTo(CombatStatus.ACTIVE);
        assertThat(result.state().activeCombatantId()).isEqualTo("hero");
        assertThat(events).noneMatch(CombatEnded.class::isInstance);

        // transition only ever carries these four fields forward, never derives them: a
        // regression that dropped one back to whatever `previous` held would still pass
        // every assertion above, and only these catch it.
        assertThat(result.state().revision()).isEqualTo(TRANSITION_REVISION);
        assertThat(result.state().dataVersion()).isEqualTo(DATA_VERSION);
        assertThat(result.state().random()).isEqualTo(TRANSITION_RANDOM);
        assertThat(result.state().counters()).isEqualTo(TRANSITION_COUNTERS);
    }

    /// Every path that lets damage land now announces the ending, which playAiPattern
    /// silently skipped: enemies won combats the client was never told about.
    @Test
    void announcesAnEndingWhateverPathReachesIt() {
        List<CombatantState> combatants =
                List.of(combatant("hero", "player", 0), combatant("enemy-0", "enemy", 3));
        List<CombatEvent> events = new ArrayList<>();

        CombatResult result = CombatEngine.transition(
                running(List.of(combatant("hero", "player", 5), combatant("enemy-0", "enemy", 3)),
                        "hero"),
                TRANSITION_REVISION, TRANSITION_RANDOM, combatants,
                List.of(new TimelineEntry("hero", 0, 0), new TimelineEntry("enemy-0", 10, 1)),
                "hero", events, TRANSITION_COUNTERS);

        assertThat(result.state().status()).isEqualTo(CombatStatus.FINISHED);
        assertThat(result.state().winnerTeamId()).isEqualTo("enemy");
        assertThat(result.state().activeCombatantId()).isNull();

        // Exactly one event lands in a list that started empty, and it is a CombatEnded
        // carrying this combat's own identifier, the revision transition was called
        // with, and the winner it computed -- not merely present among others, and not
        // a value it happened to already be holding.
        assertThat(events).hasSize(1);
        CombatEnded ended = (CombatEnded) events.get(0);
        assertThat(ended.combatId()).isEqualTo(COMBAT_ID);
        assertThat(ended.revision()).isEqualTo(TRANSITION_REVISION);
        assertThat(ended.winnerTeamId()).isEqualTo("enemy");

        assertThat(result.state().revision()).isEqualTo(TRANSITION_REVISION);
        assertThat(result.state().dataVersion()).isEqualTo(DATA_VERSION);
        assertThat(result.state().random()).isEqualTo(TRANSITION_RANDOM);
        assertThat(result.state().counters()).isEqualTo(TRANSITION_COUNTERS);
    }

    /// The turn cannot stay with a combatant its own card or its own statuses killed.
    @Test
    void handsTheTurnOnWhenThePreferredCombatantDied() {
        List<CombatantState> combatants =
                List.of(combatant("hero", "player", 0),
                        combatant("ally", "player", 4),
                        combatant("enemy-0", "enemy", 3));
        List<CombatEvent> events = new ArrayList<>();

        CombatResult result = CombatEngine.transition(
                running(List.of(combatant("hero", "player", 5), combatant("enemy-0", "enemy", 3)),
                        "hero"),
                TRANSITION_REVISION, TRANSITION_RANDOM, combatants,
                List.of(new TimelineEntry("hero", 0, 0),
                        new TimelineEntry("ally", 10, 1),
                        new TimelineEntry("enemy-0", 20, 2)),
                "hero", events, TRANSITION_COUNTERS);

        assertThat(result.state().activeCombatantId()).isEqualTo("ally");
        assertThat(result.state().status()).isEqualTo(CombatStatus.ACTIVE);

        assertThat(result.state().revision()).isEqualTo(TRANSITION_REVISION);
        assertThat(result.state().dataVersion()).isEqualTo(DATA_VERSION);
        assertThat(result.state().random()).isEqualTo(TRANSITION_RANDOM);
        assertThat(result.state().counters()).isEqualTo(TRANSITION_COUNTERS);
    }
}
