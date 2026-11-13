package fr.insastronaute.webapi.sts.combat.engine.effect;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Cutting into the turn order moves a turn; it does not hand out a second one.
 *
 * <p>Unchecked, the target's next turn is placed right after the caster's ("Placez le prochain
 * tour de la cible juste après le vôtre"); with {@code targetSelf}, the caster's next turn is
 * placed right after the target's ("Placez votre prochain tour juste après celui de la cible").
 *
 * <p>Unity shifts the entries a combatant already owns ({@code TurnSystem.cs:687-726}). The
 * handler used to append a fresh entry, leaving the combatant with two. The engine refuses a
 * timeline holding two entries for one combatant, so the next end of turn threw and the whole
 * combat became unplayable.
 */
class CutInTurnHandlerTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final int TURN_DELAY = 100;

    @Test
    void keepsOneTimelineEntryPerCombatant() {
        List<TimelineEntry> timeline = cutIn(
                "active", "latecomer", false,
                new TimelineEntry("active", 0, 0),
                new TimelineEntry("other", 200, 1),
                new TimelineEntry("latecomer", 900, 2));

        assertThat(timeline).extracting(TimelineEntry::combatantId)
                .containsExactlyInAnyOrder("active", "other", "latecomer");
    }

    /**
     * The target comes up the moment the caster's turn ends, ahead of everybody else's next
     * turn -- and never ahead of the turn under way, which the old handler did by landing the
     * target one tick before it, taking the rest of the caster's turn away.
     */
    @Test
    void putsTheTargetsNextTurnRightAfterTheCastersTurn() {
        List<TimelineEntry> timeline = cutIn(
                "active", "latecomer", false,
                new TimelineEntry("active", 0, 0),
                new TimelineEntry("other", 200, 1),
                new TimelineEntry("latecomer", 900, 2));

        assertThat(inOrder(timeline)).containsExactly("active", "latecomer", "other");
    }

    @Test
    void keepsSeveralTargetsInTheOrderTheyCutIn() {
        List<TimelineEntry> first = cutIn(
                "active", "latecomer", false,
                new TimelineEntry("active", 0, 0),
                new TimelineEntry("other", 200, 1),
                new TimelineEntry("latecomer", 900, 2),
                new TimelineEntry("straggler", 1200, 3));
        List<TimelineEntry> both = cutIn(
                "active", "straggler", false, first.toArray(TimelineEntry[]::new));

        assertThat(inOrder(both)).containsExactly("active", "latecomer", "straggler", "other");
    }

    /**
     * Self-aimed, the caster's own turn is the one under way: it cannot be rewritten, so what
     * moves is the turn it gets next, through the same pending delay endTurn folds back in.
     */
    @Test
    void putsTheCastersNextTurnRightAfterTheTargetsWhenSelfAimed() {
        List<TimelineEntry> timeline = cutIn(
                "active", "foe", true,
                new TimelineEntry("active", 0, 0),
                new TimelineEntry("other", 200, 1),
                new TimelineEntry("foe", 300, 2));

        TimelineEntry caster = entry(timeline, "active");
        assertThat(caster.readyAtTick()).as("the turn under way stays put").isZero();
        assertThat(caster.readyAtTick() + TURN_DELAY + caster.pendingSelfDelayTicks())
                .as("its next turn lands just behind the foe's")
                .isEqualTo(301);
    }

    /**
     * A self-aimed cut-in can be played outside its caster's turn -- Time Eater's Interruption
     * answers a card the player has just played. The caster's upcoming entry then simply moves,
     * right behind the target's turn under way.
     */
    @Test
    void movesACastersUpcomingTurnWhenItIsNotTheOneUnderWay() {
        List<TimelineEntry> timeline = cutIn(
                "eater", "hero", true,
                new TimelineEntry("hero", 0, 0),
                new TimelineEntry("other", 200, 1),
                new TimelineEntry("eater", 500, 2));

        assertThat(inOrder(timeline)).containsExactly("hero", "eater", "other");
        assertThat(entry(timeline, "eater").readyAtTick()).isZero();
    }

    @Test
    void leavesTheTimelineAloneWhenTheTargetHasNoTurnScheduled() {
        List<TimelineEntry> timeline = cutIn(
                "active", "latecomer", false,
                new TimelineEntry("active", 0, 0),
                new TimelineEntry("other", 200, 1));

        assertThat(timeline).hasSize(2);
    }

    private static List<String> inOrder(List<TimelineEntry> timeline) {
        return timeline.stream()
                .sorted(TimelineEntry.CANONICAL_ORDER)
                .map(TimelineEntry::combatantId)
                .toList();
    }

    private static TimelineEntry entry(List<TimelineEntry> timeline, String combatantId) {
        return timeline.stream()
                .filter(entry -> entry.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }

    private static List<TimelineEntry> cutIn(
            String sourceId, String targetId, boolean targetSelf, TimelineEntry... timeline) {
        List<CombatantState> combatants = java.util.Arrays.stream(timeline)
                .map(TimelineEntry::combatantId)
                .map(CutInTurnHandlerTest::combatant)
                .toList();
        CombatantState target = combatant(targetId);
        return new CutInTurnHandler().handle(
                COMBAT_ID,
                1L,
                sourceId,
                target,
                new EffectDefinition(EffectType.CUT_IN_TURN, 0, targetSelf, null, 0, "", null),
                new RandomState(3),
                combatants,
                List.of(timeline),
                null,
                List.of()).timeline();
    }

    private static CombatantState combatant(String combatantId) {
        return new CombatantState(
                combatantId,
                combatantId.equals("hero") || combatantId.equals("active") ? "players" : "enemies",
                ControllerType.AI,
                20,
                20,
                0,
                0,
                0,
                TURN_DELAY,
                new CardPiles(List.of(), List.of(), List.of(), List.of()));
    }
}
