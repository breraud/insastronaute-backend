package fr.insastronaute.webapi.sts.multiplayer.combat;

import static org.assertj.core.api.Assertions.assertThat;

import fr.insastronaute.webapi.sts.combat.event.CardDrawn;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.DamageApplied;
import fr.insastronaute.webapi.sts.combat.event.PileShuffled;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * What a player may be told of what just happened.
 *
 * <p>The events are what the client replays to animate anything at all; without them a PvP
 * combat jumps from state to state with nothing moving. But an event names cards, and a card
 * drawn by the opponent is a card this player must not see -- the view goes to the trouble of
 * hiding the opponent's hand, and an unfiltered event stream would hand it over anyway.
 */
class StsPvpEventBroadcastTest {

    private static final UUID COMBAT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000502");

    @Test
    void sendsWhatHappenedOnTheTable() {
        List<CombatEvent> visible = StsPvpCombatBroadcaster.visibleTo(
                List.of(new DamageApplied(COMBAT_ID, 2L, "player-a", "player-b", 6, 0, 6, 0, 19)),
                "player-b");

        assertThat(visible).hasSize(1);
    }

    @Test
    void hidesTheCardsTheOpponentDrew() {
        List<CombatEvent> visible = StsPvpCombatBroadcaster.visibleTo(
                List.of(new CardDrawn(COMBAT_ID, 2L, "player-a", "inst-1", "katana", 0)),
                "player-b");

        assertThat(visible)
                .as("naming the card the opponent drew undoes the whole point of the view")
                .isEmpty();
    }

    /**
     * A reshuffle names the whole new order of the draw pile, and that order is the opponent's
     * next draws laid out in advance.
     *
     * <p>The ids are opaque, which is why this slipped through -- but they are only opaque until
     * they are matched up. The view shows an opponent's discard pile in full, and every CardPlayed
     * ties a cardInstanceId to its definitionId, so a client that keeps notes builds the mapping
     * on its own and reads the shuffle straight off.
     */
    @Test
    void hidesTheOrderTheOpponentsReshuffleProduced() {
        List<CombatEvent> visible = StsPvpCombatBroadcaster.visibleTo(
                List.of(new PileShuffled(
                        COMBAT_ID, 2L, "player-a", "DRAW",
                        List.of("inst-1", "inst-2", "inst-3"))),
                "player-b");

        assertThat(visible)
                .as("the opponent's next draws, in order, is the one thing the view exists to hide")
                .isEmpty();
    }

    @Test
    void showsAPlayerTheirOwnReshuffle() {
        List<CombatEvent> visible = StsPvpCombatBroadcaster.visibleTo(
                List.of(new PileShuffled(
                        COMBAT_ID, 2L, "player-a", "DRAW", List.of("inst-1", "inst-2"))),
                "player-a");

        assertThat(visible).hasSize(1);
    }

    @Test
    void showsAPlayerTheirOwnDraws() {
        List<CombatEvent> visible = StsPvpCombatBroadcaster.visibleTo(
                List.of(new CardDrawn(COMBAT_ID, 2L, "player-a", "inst-1", "katana", 0)),
                "player-a");

        assertThat(visible).hasSize(1);
    }
}
