package fr.insastronaute.webapi.sts.multiplayer.combat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * A PvP turn lasts 30 seconds and the client cannot count them down without being told when
 * they end. The deadline existed only in the HTTP DTO, never in the view pushed on the socket,
 * so a player lost the turn without ever seeing it coming.
 *
 * <p>serverTime travels beside it because the client's clock is not the server's, and a
 * countdown computed against the wrong one is wrong by exactly that drift.
 */
class StsPvpCombatViewDeadlineTest {

    @Test
    void carriesTheDeadlineAndTheServerTime() {
        Instant deadline = Instant.parse("2026-08-24T12:00:30Z");
        Instant now = Instant.parse("2026-08-24T12:00:00Z");

        StsPvpCombatView view = StsPvpCombatViewFixtures.forViewer(deadline, now);

        assertThat(view.turnDeadline()).isEqualTo(deadline);
        assertThat(view.serverTime()).isEqualTo(now);
    }

    /** A battle with no deadline yet -- before the first turn opens -- says so rather than lying. */
    @Test
    void reportsNoDeadlineRatherThanAnInventedOne() {
        Instant now = Instant.parse("2026-08-24T12:00:00Z");

        StsPvpCombatView view = StsPvpCombatViewFixtures.forViewer(null, now);

        assertThat(view.turnDeadline()).isNull();
        assertThat(view.serverTime()).isEqualTo(now);
    }

    /**
     * A live two-player PvP combat projected for its first player, with the clock values under
     * test. It lives in the test because it exists for the test: production has a battle to read
     * the deadline off, and a fixture in main/ would be a class nothing ships uses.
     */
    private static final class StsPvpCombatViewFixtures {

        private StsPvpCombatViewFixtures() {
        }

        static StsPvpCombatView forViewer(Instant turnDeadline, Instant serverTime) {
            return StsPvpCombatView.forViewer(
                    StsPvpTestStates.twoHumans("player-a", "player-b"),
                    "player-a",
                    turnDeadline,
                    serverTime);
        }
    }
}
