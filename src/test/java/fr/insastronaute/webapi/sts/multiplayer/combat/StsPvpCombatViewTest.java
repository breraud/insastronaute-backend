package fr.insastronaute.webapi.sts.multiplayer.combat;

import fr.insastronaute.webapi.sts.combat.model.CombatState;

import java.time.Instant;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatView.StsPvpCombatantView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The fog of war a PvP combat is served through.
 *
 * <p>A raw {@link CombatState} carries both players' hands and the deterministic seed the whole
 * future draw order can be computed from. Handing it to a player is an advantage no server-side
 * rule can detect afterwards, so what leaves the server is a state projected for its reader.
 */
class StsPvpCombatViewTest {

    @Test
    void aViewerSeesItsOwnHandInFull() {
        CombatState state = StsPvpTestStates.twoHumans("player-a", "player-b");

        StsPvpCombatView view = forViewer(state, "player-a");

        StsPvpCombatantView own = view.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals("player-a"))
                .findFirst()
                .orElseThrow();
        assertThat(own.piles()).isNotNull();
        assertThat(own.hiddenPiles()).isNull();
        assertThat(own.piles().hand()).isNotEmpty();
    }

    @Test
    void aViewerNeverSeesTheOpponentHandOrDrawOrder() {
        CombatState state = StsPvpTestStates.twoHumans("player-a", "player-b");

        StsPvpCombatView view = forViewer(state, "player-a");

        StsPvpCombatantView opponent = view.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals("player-b"))
                .findFirst()
                .orElseThrow();
        assertThat(opponent.piles()).isNull();
        assertThat(opponent.hiddenPiles()).isNotNull();
        assertThat(opponent.hiddenPiles().handCount())
                .isEqualTo(state.combatants().stream()
                        .filter(combatant -> combatant.combatantId().equals("player-b"))
                        .findFirst().orElseThrow().piles().hand().size());
    }

    @Test
    void theSerializedViewLeaksNeitherTheOpponentCardsNorTheRandomSeed() throws Exception {
        CombatState state = StsPvpTestStates.twoHumans("player-a", "player-b");
        String opponentCardInstanceId = state.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals("player-b"))
                .findFirst().orElseThrow()
                .piles().hand().getFirst().instanceId();

        String json = objectMapper()
                .writeValueAsString(forViewer(state, "player-a"));

        assertThat(json).doesNotContain(opponentCardInstanceId);
        assertThat(json).doesNotContain("random");
        assertThat(json).doesNotContain(String.valueOf(state.random().value()));
    }

    @Test
    void theCountersACardScalesOnTravelWithTheView() throws Exception {
        CombatState state = StsPvpTestStates.twoHumans("player-a", "player-b");

        StsPvpCombatView view = forViewer(state, "player-a");

        // A card reading "one more damage per energy spent this turn" cannot be rendered without
        // them, and none of them says anything the view does not already show.
        assertThat(view.counters()).isEqualTo(state.counters());
        String json = objectMapper().writeValueAsString(view);
        assertThat(json).contains("energySpentThisTurn", "cardsPlayedThisTurn", "hpLostSinceLastTurn");
    }

    @Test
    void aViewerThatIsNotInTheCombatIsRefused() {
        CombatState state = StsPvpTestStates.twoHumans("player-a", "player-b");

        assertThatThrownBy(() -> forViewer(state, "player-c"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * The clock is not what these tests are about, so it is held still here: what they check is
     * what the projection hides, and the deadline hides nothing.
     */
    private static StsPvpCombatView forViewer(CombatState state, String viewerCombatantId) {
        return StsPvpCombatView.forViewer(
                state,
                viewerCombatantId,
                Instant.parse("2026-08-24T12:00:30Z"),
                Instant.parse("2026-08-24T12:00:00Z"));
    }

    /**
     * A mapper with the modules the application's own has: the view carries {@link Instant}
     * fields, and a bare mapper refuses them where the one Spring configures writes them as ISO
     * strings. Serializing with less than production does would be testing a different object.
     */
    private static com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
    }
}
