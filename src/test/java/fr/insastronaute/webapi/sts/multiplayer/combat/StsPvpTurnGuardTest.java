package fr.insastronaute.webapi.sts.multiplayer.combat;

import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.ForbiddenException;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StsPvpTurnGuardTest {

    @Test
    void namesTheTimelineActiveCombatant() {
        CombatState state = StsPvpTestStates.twoHumans("player-a", "player-b");

        assertThat(StsPvpTurnGuard.activeCombatantId(state)).isEqualTo("player-a");
    }

    @Test
    void acceptsACommandFromTheActiveCombatant() {
        CombatState state = StsPvpTestStates.twoHumans("player-a", "player-b");

        assertThatCode(() -> StsPvpTurnGuard.requireActiveCombatant(state, "player-a"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsACommandFromTheWaitingCombatant() {
        CombatState state = StsPvpTestStates.twoHumans("player-a", "player-b");

        assertThatThrownBy(() -> StsPvpTurnGuard.requireActiveCombatant(state, "player-b"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("tour");
    }

    @Test
    void rejectsAStaleRevision() {
        CombatState state = StsPvpTestStates.twoHumans("player-a", "player-b");

        assertThatCode(() -> StsPvpTurnGuard.requireCurrentRevision(state, state.revision()))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> StsPvpTurnGuard.requireCurrentRevision(state, state.revision() - 1))
                .isInstanceOf(ConflictException.class);
    }
}
