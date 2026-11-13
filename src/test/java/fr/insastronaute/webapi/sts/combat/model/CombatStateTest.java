package fr.insastronaute.webapi.sts.combat.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatStateTest {

    @Test
    void defensivelyCopiesAllSourceLists() {
        List<CardInstance> draw = new ArrayList<>(List.of(card("draw")));
        List<CardInstance> hand = new ArrayList<>(List.of(card("hand")));
        List<CardInstance> discard = new ArrayList<>(List.of(card("discard")));
        List<CardInstance> exhaust = new ArrayList<>(List.of(card("exhaust")));
        CardPiles piles = new CardPiles(draw, hand, discard, exhaust);
        CombatantState combatant = combatant("hero", piles);
        CombatantState enemy = combatant("enemy", "enemies", 10);
        List<CombatantState> combatants = new ArrayList<>(List.of(combatant, enemy));
        List<TimelineEntry> timeline = new ArrayList<>(List.of(
                new TimelineEntry("hero", 0, 0),
                new TimelineEntry("enemy", 1, 1)));

        CombatState state = activeState(combatants, timeline, "hero");
        draw.clear();
        hand.clear();
        discard.clear();
        exhaust.clear();
        combatants.clear();
        timeline.clear();

        assertThat(piles.draw()).containsExactly(card("draw"));
        assertThat(piles.hand()).containsExactly(card("hand"));
        assertThat(piles.discard()).containsExactly(card("discard"));
        assertThat(piles.exhaust()).containsExactly(card("exhaust"));
        assertThat(state.combatants()).containsExactly(combatant, enemy);
        assertThat(state.timeline()).containsExactly(
                new TimelineEntry("hero", 0, 0),
                new TimelineEntry("enemy", 1, 1));
        assertThatThrownBy(() -> state.combatants().add(combatant))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> state.timeline().add(new TimelineEntry("hero", 1, 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsBlankIdentifiers() {
        assertThatIllegalArgumentException().isThrownBy(() -> new CardInstance(" ", "strike"));
        assertThatIllegalArgumentException().isThrownBy(() -> new CardInstance("card", "\t"));
        assertThatIllegalArgumentException().isThrownBy(() -> new TimelineEntry("", 0, 0));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new CombatantState("hero", " ", ControllerType.HUMAN, 10, 10, 0, 3, 3, 1, emptyPiles()));
        assertThatIllegalArgumentException().isThrownBy(() -> activeState(List.of(combatant("hero")), List.of(), " "));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new CombatState(UUID.randomUUID(), 0, "", CombatStatus.FINISHED, null,
                        new RandomState(1), List.of(combatant("hero")), List.of(), null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new CombatState(UUID.randomUUID(), 0, "v1", CombatStatus.FINISHED, " ",
                        new RandomState(1), List.of(combatant("hero")), List.of(), null));
    }

    @Test
    void rejectsNullRequiredValuesAndLists() {
        assertThatNullPointerException().isThrownBy(() -> new CardPiles(null, List.of(), List.of(), List.of()));
        assertThatNullPointerException().isThrownBy(
                () -> new CombatantState("hero", "players", null, 10, 10, 0, 3, 3, 1, emptyPiles()));
        assertThatNullPointerException().isThrownBy(
                () -> new CombatState(null, 0, "v1", CombatStatus.FINISHED, null,
                        new RandomState(1), List.of(combatant("hero")), List.of(), null));
        assertThatNullPointerException().isThrownBy(
                () -> new CombatState(UUID.randomUUID(), 0, "v1", null, null,
                        new RandomState(1), List.of(combatant("hero")), List.of(), null));
        assertThatNullPointerException().isThrownBy(
                () -> new CombatState(UUID.randomUUID(), 0, "v1", CombatStatus.FINISHED, null,
                        null, List.of(combatant("hero")), List.of(), null));
    }

    @Test
    void rejectsInvalidNumericValues() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new CombatantState("hero", "players", ControllerType.HUMAN, -1, 0, 0, 3, 3, 1, emptyPiles()));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new CombatantState("hero", "players", ControllerType.HUMAN, 10, -1, 0, 3, 3, 1, emptyPiles()));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new CombatantState("hero", "players", ControllerType.HUMAN, 10, 11, 0, 3, 3, 1, emptyPiles()));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new CombatantState("hero", "players", ControllerType.HUMAN, 10, 10, -1, 3, 3, 1, emptyPiles()));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new CombatantState("hero", "players", ControllerType.HUMAN, 10, 10, 0, -1, 3, 1, emptyPiles()));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new CombatantState("hero", "players", ControllerType.HUMAN, 10, 10, 0, 3, -1, 1, emptyPiles()));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new CombatantState("hero", "players", ControllerType.HUMAN, 10, 10, 0, 3, 3, 0, emptyPiles()));
        assertThatIllegalArgumentException().isThrownBy(() -> new TimelineEntry("hero", -1, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new TimelineEntry("hero", 0, -1));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new CombatState(UUID.randomUUID(), -1, "v1", CombatStatus.FINISHED, null,
                        new RandomState(1), List.of(combatant("hero")), List.of(), null));
    }

    @Test
    void rejectsDuplicateCombatantIdentifiers() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> activeState(List.of(combatant("hero"), combatant("hero")), List.of(), "hero"));
    }

    @Test
    void activeStateRequiresAnExistingActiveCombatant() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> activeState(List.of(combatant("hero")), List.of(), null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> activeState(List.of(combatant("hero")), List.of(), "missing"));
    }

    @Test
    void activeStateRequiresALivingActiveCombatantWithExactlyOneTimelineEntry() {
        CombatantState deadHero = withHp(combatant("hero"), 0);

        assertThatIllegalArgumentException().isThrownBy(
                () -> activeState(List.of(deadHero), List.of(new TimelineEntry("hero", 0, 0)), "hero"));
        assertThatIllegalArgumentException().isThrownBy(
                () -> activeState(List.of(combatant("hero")), List.of(), "hero"));
        assertThatIllegalArgumentException().isThrownBy(
                () -> activeState(
                        List.of(combatant("hero")),
                        List.of(
                                new TimelineEntry("hero", 0, 0),
                                new TimelineEntry("hero", 1, 1)),
                        "hero"));
    }

    @Test
    void activeStateRejectsAWinnerAndUnsafeTransitionCounters() {
        List<CombatantState> combatants = List.of(combatant("hero"));
        List<TimelineEntry> timeline = List.of(new TimelineEntry("hero", 0, 0));

        assertThatIllegalArgumentException().isThrownBy(() -> new CombatState(
                UUID.randomUUID(), 0, "v1", CombatStatus.ACTIVE, "players",
                new RandomState(1), combatants, timeline, "hero"));
        assertThatIllegalArgumentException().isThrownBy(() -> new CombatState(
                UUID.randomUUID(), Long.MAX_VALUE, "v1", CombatStatus.ACTIVE, null,
                new RandomState(1), combatants, timeline, "hero"));
        assertThatIllegalArgumentException().isThrownBy(() -> new CombatState(
                UUID.randomUUID(), 0, "v1", CombatStatus.ACTIVE, null,
                new RandomState(1), combatants,
                List.of(new TimelineEntry("hero", 0, Long.MAX_VALUE)), "hero"));
        assertThatIllegalArgumentException().isThrownBy(() -> new CombatState(
                UUID.randomUUID(), 0, "v1", CombatStatus.ACTIVE, null,
                new RandomState(1), combatants,
                List.of(new TimelineEntry("hero", Long.MAX_VALUE, 0)), "hero"));
    }

    @Test
    void activeStateRequiresAtLeastTwoLivingTeams() {
        List<CombatantState> combatants = List.of(
                combatant("hero"),
                combatant("enemy", "enemies", 0));

        assertThatIllegalArgumentException().isThrownBy(
                () -> activeState(
                        combatants,
                        List.of(
                                new TimelineEntry("hero", 0, 0),
                                new TimelineEntry("enemy", 1, 1)),
                        "hero"));
    }

    @Test
    void activeCombatantMustBeTheFirstLivingCanonicalTimelineEntry() {
        List<CombatantState> combatants = List.of(
                combatant("hero"),
                combatant("enemy", "enemies", 10));

        assertThatIllegalArgumentException().isThrownBy(
                () -> activeState(
                        combatants,
                        List.of(
                                new TimelineEntry("enemy", 0, 0),
                                new TimelineEntry("hero", 10, 1)),
                        "hero"));
    }

    @Test
    void rejectsUnknownDuplicateOrNonCanonicalTimelineEntries() {
        List<CombatantState> combatants = List.of(
                combatant("hero"),
                combatant("enemy", "enemies", 10));

        assertThatIllegalArgumentException().isThrownBy(
                () -> activeState(combatants, List.of(
                        new TimelineEntry("hero", 0, 0),
                        new TimelineEntry("missing", 1, 1)), "hero"));
        assertThatIllegalArgumentException().isThrownBy(
                () -> activeState(combatants, List.of(
                        new TimelineEntry("hero", 0, 0),
                        new TimelineEntry("enemy", 1, 1),
                        new TimelineEntry("enemy", 2, 2)), "hero"));
        assertThatIllegalArgumentException().isThrownBy(
                () -> activeState(combatants, List.of(
                        new TimelineEntry("enemy", 1, 1),
                        new TimelineEntry("hero", 0, 0)), "hero"));
        assertThatIllegalArgumentException().isThrownBy(
                () -> activeState(combatants, List.of(
                        new TimelineEntry("hero", 0, 0)), "hero"));
    }

    @Test
    void finishedStateRequiresNoActiveCombatantAndACoherentWinner() {
        CombatantState winner = combatant("hero");
        CombatantState loser = combatant("enemy", "enemies", 0);
        List<CombatantState> combatants = List.of(winner, loser);
        List<TimelineEntry> timeline = List.of(
                new TimelineEntry("hero", 0, 0),
                new TimelineEntry("enemy", 1, 1));

        assertThatIllegalArgumentException().isThrownBy(() -> finishedState(
                combatants, timeline, "players", "hero"));
        assertThatIllegalArgumentException().isThrownBy(() -> finishedState(
                combatants, timeline, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> finishedState(
                combatants, timeline, "enemies", null));
        assertThatIllegalArgumentException().isThrownBy(() -> finishedState(
                List.of(winner, combatant("enemy", "enemies", 10)),
                timeline, "players", null));

        assertThat(finishedState(combatants, timeline, "players", null).winnerTeamId())
                .isEqualTo("players");
        assertThat(finishedState(
                List.of(withHp(winner, 0), loser), timeline, null, null).winnerTeamId())
                .isNull();
    }

    private static CardInstance card(String id) {
        return new CardInstance(id, "strike");
    }

    private static CardPiles emptyPiles() {
        return new CardPiles(List.of(), List.of(), List.of(), List.of());
    }

    private static CombatantState combatant(String id) {
        return combatant(id, emptyPiles());
    }

    private static CombatantState combatant(String id, CardPiles piles) {
        return new CombatantState(id, "players", ControllerType.HUMAN, 10, 10, 0, 3, 3, 1, piles);
    }

    private static CombatantState combatant(String id, String teamId, int hp) {
        return new CombatantState(
                id, teamId, ControllerType.HUMAN, 10, hp, 0, 3, 3, 1, emptyPiles());
    }

    private static CombatantState withHp(CombatantState combatant, int hp) {
        return new CombatantState(
                combatant.combatantId(),
                combatant.teamId(),
                combatant.controllerType(),
                combatant.maxHp(),
                hp,
                combatant.armor(),
                combatant.energy(),
                combatant.baseEnergy(),
                combatant.turnDelayTicks(),
                combatant.piles());
    }

    private static CombatState finishedState(
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            String winnerTeamId,
            String activeCombatantId
    ) {
        return new CombatState(
                UUID.randomUUID(),
                0,
                "v1",
                CombatStatus.FINISHED,
                winnerTeamId,
                new RandomState(1),
                combatants,
                timeline,
                activeCombatantId);
    }

    private static CombatState activeState(
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            String activeCombatantId
    ) {
        return new CombatState(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                0,
                "v1",
                CombatStatus.ACTIVE,
                null,
                new RandomState(42),
                combatants,
                timeline,
                activeCombatantId
        );
    }
}
