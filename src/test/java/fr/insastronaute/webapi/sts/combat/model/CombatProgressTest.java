package fr.insastronaute.webapi.sts.combat.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;
import org.junit.jupiter.api.Test;

class CombatProgressTest {

    private static CombatantState combatant(String id, String team, int hp) {
        return new CombatantState(id, team, ControllerType.AI, 10, hp, 0, 0, 0, 25,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
    }

    @Test
    void aCombatWithTwoTeamsStandingIsNotOver() {
        CombatProgress progress = CombatProgress.of(
                List.of(combatant("hero", "player", 5), combatant("enemy-0", "enemy", 3)),
                List.of(new TimelineEntry("hero", 0, 0), new TimelineEntry("enemy-0", 10, 1)));

        assertThat(progress.finished()).isFalse();
        assertThat(progress.winnerTeamId()).isNull();
    }

    @Test
    void theLastTeamStandingWins() {
        CombatProgress progress = CombatProgress.of(
                List.of(combatant("hero", "player", 5), combatant("enemy-0", "enemy", 0)),
                List.of(new TimelineEntry("hero", 0, 0), new TimelineEntry("enemy-0", 10, 1)));

        assertThat(progress.finished()).isTrue();
        assertThat(progress.winnerTeamId()).isEqualTo("player");
    }

    /// A combat nobody survived is a draw, and a draw has no winner to invent.
    @Test
    void everybodyDyingAtOnceLeavesNoWinner() {
        CombatProgress progress = CombatProgress.of(
                List.of(combatant("hero", "player", 0), combatant("enemy-0", "enemy", 0)),
                List.of(new TimelineEntry("hero", 0, 0), new TimelineEntry("enemy-0", 10, 1)));

        assertThat(progress.finished()).isTrue();
        assertThat(progress.winnerTeamId()).isNull();
    }

    /// The dead leave the timeline of a combat that is over, so the finished state can
    /// be built without them.
    @Test
    void aFinishedCombatDropsTheDeadFromItsTimeline() {
        CombatProgress progress = CombatProgress.of(
                List.of(combatant("hero", "player", 5), combatant("enemy-0", "enemy", 0)),
                List.of(new TimelineEntry("hero", 0, 0), new TimelineEntry("enemy-0", 10, 1)));

        assertThat(progress.timeline()).extracting(TimelineEntry::combatantId)
                .containsExactly("hero");
    }

    /// While the combat runs the dead keep their place: CombatState asks only that the
    /// active combatant be the first *living* entry, never that the list hold no corpse.
    @Test
    void aRunningCombatKeepsTheDeadOnItsTimeline() {
        CombatProgress progress = CombatProgress.of(
                List.of(combatant("hero", "player", 5),
                        combatant("ally", "player", 0),
                        combatant("enemy-0", "enemy", 3)),
                List.of(new TimelineEntry("ally", 0, 0),
                        new TimelineEntry("hero", 10, 1),
                        new TimelineEntry("enemy-0", 20, 2)));

        assertThat(progress.timeline()).extracting(TimelineEntry::combatantId)
                .containsExactly("ally", "hero", "enemy-0");
    }

    /// Every case above hands CombatProgress.of a timeline already in canonical order,
    /// so TimelineOperations.sorted's own isSorted check short-circuits before the sort
    /// ever runs: the wiring that calls it -- the right call, on the right list -- goes
    /// unexercised. This is the running branch, where the timeline reaches sorted(...)
    /// unfiltered.
    @Test
    void aRunningCombatsTimelineComesBackInCanonicalOrderRegardlessOfInputOrder() {
        CombatProgress progress = CombatProgress.of(
                List.of(combatant("hero", "player", 5), combatant("enemy-0", "enemy", 3)),
                List.of(new TimelineEntry("enemy-0", 10, 1), new TimelineEntry("hero", 0, 0)));

        assertThat(progress.timeline()).extracting(TimelineEntry::combatantId)
                .containsExactly("hero", "enemy-0");
    }

    /// The finished branch sorts a *filtered* list, a different expression from the
    /// running branch's unfiltered one; a mistake specific to that side of the ternary --
    /// filtering without sorting, or sorting before filtering drops the ordering again --
    /// would not show up in the running case above.
    @Test
    void aFinishedCombatsTimelineComesBackInCanonicalOrderRegardlessOfInputOrder() {
        CombatProgress progress = CombatProgress.of(
                List.of(combatant("hero", "player", 5),
                        combatant("ally", "player", 5),
                        combatant("enemy-0", "enemy", 0)),
                List.of(new TimelineEntry("ally", 10, 1),
                        new TimelineEntry("enemy-0", 5, 2),
                        new TimelineEntry("hero", 0, 0)));

        assertThat(progress.timeline()).extracting(TimelineEntry::combatantId)
                .containsExactly("hero", "ally");
    }

    @Test
    void theTurnStaysWithThePreferredCombatantWhileItLives() {
        List<CombatantState> combatants =
                List.of(combatant("hero", "player", 5), combatant("enemy-0", "enemy", 3));
        CombatProgress progress = CombatProgress.of(combatants,
                List.of(new TimelineEntry("hero", 0, 0), new TimelineEntry("enemy-0", 10, 1)));

        assertThat(progress.nextActiveCombatantId("hero", combatants)).isEqualTo("hero");
    }

    /// The defect that stuck a player in production: a combatant killed by its own
    /// statuses kept the place it had just been given back, ahead of everybody, and the
    /// turn went to that corpse.
    @Test
    void theTurnSkipsAPreferredCombatantThatDied() {
        List<CombatantState> combatants =
                List.of(combatant("hero", "player", 0),
                        combatant("ally", "player", 4),
                        combatant("enemy-0", "enemy", 3));
        CombatProgress progress = CombatProgress.of(combatants,
                List.of(new TimelineEntry("hero", 0, 0),
                        new TimelineEntry("ally", 10, 1),
                        new TimelineEntry("enemy-0", 20, 2)));

        assertThat(progress.nextActiveCombatantId("hero", combatants)).isEqualTo("ally");
    }

    @Test
    void aTimelineWithoutAnybodyAliveIsABrokenState() {
        List<CombatantState> combatants = List.of(combatant("hero", "player", 0));

        CombatProgress progress = CombatProgress.of(
                combatants, List.of(new TimelineEntry("hero", 0, 0)));

        assertThatIllegalStateException()
                .isThrownBy(() -> progress.nextActiveCombatantId("hero", combatants))
                .withMessageContaining("living timeline entry");
    }
}
