package fr.insastronaute.webapi.sts.multiplayer.combat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.mock;

import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.sts.combat.command.CombatCommand;
import fr.insastronaute.webapi.sts.combat.event.CombatEnded;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatService.StsPvpCommandOutcome;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpSeat;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattle;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattleStatus;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpBattleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The two sweeps that keep a PvP battle from stalling on a player who stopped answering.
 *
 * <p>The policy the spec settles on is deliberately asymmetric: a turn that runs out of time
 * costs its player the turn and nothing more, while a player who has gone silent long enough
 * loses the match. Both sweeps are exercised here through a mocked combat service, because what
 * is under test is the decision — who is late, who is gone, and what is done about it — and not
 * the engine, which has its own tests.
 */
@ExtendWith(MockitoExtension.class)
class StsPvpBattleTimeoutSchedulerTest {

    private static final UUID BATTLE_ID = UUID.fromString("5c0b8e11-0000-4000-8000-00000000000a");
    private static final UUID PLAYER_A = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PLAYER_B = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String SUB_A = "sub-a";
    private static final String SUB_B = "sub-b";
    private static final Instant NOW = Instant.parse("2026-08-22T10:15:30Z");
    private static final Duration GRACE = Duration.ofSeconds(120L);

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock private StsPvpBattleRepository battleRepository;
    @Mock private StsPvpCombatService combatService;
    @Mock private StsPvpCombatBroadcaster broadcaster;

    private StsPvpBattleTimeoutScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new StsPvpBattleTimeoutScheduler(
                battleRepository, combatService, broadcaster, GRACE.toSeconds(),
                mock(AutoChallengeTriggerPublisher.class));
        TimeTravelContext.setForcedTime(NOW);
    }

    @AfterEach
    void tearDown() {
        TimeTravelContext.clear();
    }

    @Test
    void anExpiredTurnEndsThatPlayersTurn() {
        StsPvpBattle battle = battle();
        battle.setTurnDeadline(NOW.minusSeconds(1L));
        theSweepReaches(battle);
        CombatState state = liveCombat();
        when(combatService.currentStateIfAny(battle)).thenReturn(Optional.of(state));
        when(combatService.applyCommand(eq(battle), eq(PLAYER_A), any()))
                .thenReturn(new StsPvpCommandOutcome(state, List.of(), false));

        scheduler.sweepExpiredTurns();

        ArgumentCaptor<CombatCommand> command = ArgumentCaptor.forClass(CombatCommand.class);
        verify(combatService).applyCommand(eq(battle), eq(PLAYER_A), command.capture());
        assertThat(command.getValue()).isInstanceOf(EndTurnCommand.class);
        // The command must be the one the engine's own guard accepts: the timeline-active
        // combatant, on the revision the stored state is at.
        assertThat(command.getValue().actorId()).isEqualTo(PLAYER_A.toString());
        assertThat(command.getValue().combatId()).isEqualTo(state.combatId());
        assertThat(command.getValue().expectedRevision()).isEqualTo(state.revision());
        // Losing the turn is not losing the match.
        assertThat(battle.getStatus()).isEqualTo(StsPvpBattleStatus.ACTIVE);
        assertThat(battle.getWinnerTeam()).isNull();
        verify(battleRepository).save(battle);
        verify(broadcaster).publish(battle, state, null);
    }

    @Test
    void aTurnStillInsideItsDeadlineIsLeftAlone() {
        StsPvpBattle battle = battle();
        battle.setTurnDeadline(NOW.plusSeconds(10L));
        theSweepReaches(battle);

        scheduler.sweepExpiredTurns();

        verify(combatService, never()).applyCommand(any(), any(), any());
        verify(battleRepository, never()).save(any());
        verifyNoInteractions(broadcaster);
    }

    @Test
    void aPlayerWhoseHeartbeatWentStaleForfeits() {
        StsPvpBattle battle = battle();
        battle.setTurnDeadline(NOW.plusSeconds(10L));
        battle.setHeartbeat(heartbeat(NOW, NOW.minus(GRACE).minusSeconds(1L)));
        theSweepReaches(battle);
        when(combatService.seats(battle)).thenReturn(participants());
        when(combatService.currentStateIfAny(battle)).thenReturn(Optional.of(liveCombat()));
        scheduler.sweepDisconnectedPlayers();

        assertThat(battle.getStatus()).isEqualTo(StsPvpBattleStatus.FINISHED);
        assertThat(battle.getWinnerTeam()).isZero();
        assertThat(battle.getTurnDeadline()).isNull();
        // Le bareme lui-meme ne se verifie plus ici : cette veille designe le vainqueur et
        // demande le reglement, elle ne le calcule pas. Ce qu'un forfait vaut est verifie la ou
        // il se calcule -- StsPvpRatingTest et StsPvpBattleSettlementTest -- et ce qui compte
        // ici est que le forfait passe par le meme reglement qu'un combat que le moteur termine.
        verify(combatService).settleRewards(battle, 0);
        verify(battleRepository).save(battle);

        // The combat itself is written off too, so the player who came back cannot keep playing a
        // battle that is over, and so both clients are told the same thing.
        ArgumentCaptor<CombatState> conceded = ArgumentCaptor.forClass(CombatState.class);
        verify(combatService).storeState(eq(battle), conceded.capture());
        assertThat(conceded.getValue().status()).isEqualTo(CombatStatus.FINISHED);
        assertThat(conceded.getValue().winnerTeamId()).isEqualTo("team-0");
        assertThat(combatant(conceded.getValue(), PLAYER_B).hp()).isZero();
        assertThat(combatant(conceded.getValue(), PLAYER_A).hp()).isPositive();
        verify(broadcaster).publish(eq(battle), eq(conceded.getValue()), eq(null));
    }

    /**
     * A forfeit says out loud that the combat is over.
     *
     * <p>The state alone was published, and the client reads the outcome from the CombatEnded
     * event rather than from the state, so a deserted battle left both screens showing a fight
     * still under way. The announcement goes out ahead of the state for the same reason every
     * other event does: it is what the client plays the ending with.
     */
    @Test
    void aForfeitAnnouncesTheEndBeforeTheStateItEndsOn() {
        StsPvpBattle battle = battle();
        battle.setHeartbeat(heartbeat(NOW, NOW.minus(GRACE).minusSeconds(1L)));
        theSweepReaches(battle);
        when(combatService.seats(battle)).thenReturn(participants());
        when(combatService.currentStateIfAny(battle)).thenReturn(Optional.of(liveCombat()));

        scheduler.sweepDisconnectedPlayers();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CombatEvent>> announced = ArgumentCaptor.forClass(List.class);
        InOrder order = inOrder(broadcaster);
        order.verify(broadcaster).publishEvents(eq(battle), announced.capture(), eq(null));
        order.verify(broadcaster).publish(eq(battle), any(CombatState.class), eq(null));

        assertThat(announced.getValue())
                .as("a client that reads the outcome from the event is told nothing without it")
                .anySatisfy(event -> assertThat(event)
                        .isInstanceOfSatisfying(CombatEnded.class,
                                ended -> assertThat(ended.winnerTeamId()).isEqualTo("team-0")));
    }

    @Test
    void aBattleBothPlayersLeftIsAbandonedWithoutAWinner() {
        StsPvpBattle battle = battle();
        battle.setHeartbeat(heartbeat(NOW.minus(GRACE).minusSeconds(1L), NOW.minus(GRACE).minusSeconds(1L)));
        theSweepReaches(battle);
        when(combatService.seats(battle)).thenReturn(participants());

        scheduler.sweepDisconnectedPlayers();

        // Nobody is left to be declared the winner, so nothing is settled at all.
        assertThat(battle.getStatus()).isEqualTo(StsPvpBattleStatus.ABANDONED);
        assertThat(battle.getWinnerTeam()).isNull();
        assertThat(battle.getTurnDeadline()).isNull();
        verify(combatService, never()).settleRewards(any(), anyInt());
        verify(battleRepository).save(battle);
    }

    @Test
    void aBattleWhoseTwoPlayersAreStillThereIsLeftAlone() {
        StsPvpBattle battle = battle();
        battle.setHeartbeat(heartbeat(NOW.minusSeconds(30L), NOW.minusSeconds(30L)));
        theSweepReaches(battle);
        when(combatService.seats(battle)).thenReturn(participants());

        scheduler.sweepDisconnectedPlayers();

        assertThat(battle.getStatus()).isEqualTo(StsPvpBattleStatus.ACTIVE);
        verify(battleRepository, never()).save(any());
        verify(combatService, never()).settleRewards(any(), anyInt());
        verifyNoInteractions(broadcaster);
    }

    private void theSweepReaches(StsPvpBattle battle) {
        when(battleRepository.findIdsByStatus(StsPvpBattleStatus.ACTIVE)).thenReturn(List.of(BATTLE_ID));
        when(battleRepository.findByIdWithLock(BATTLE_ID)).thenReturn(Optional.of(battle));
    }

    private static CombatState liveCombat() {
        return StsPvpTestStates.twoHumans(PLAYER_A.toString(), PLAYER_B.toString());
    }

    private static List<StsPvpSeat> participants() {
        return List.of(
                StsPvpSeat.human(PLAYER_A, SUB_A, "A", "EP", 0),
                StsPvpSeat.human(PLAYER_B, SUB_B, "B", "EP", 1));
    }

    private static CombatantState combatant(CombatState state, UUID userId) {
        return state.combatants().stream()
                .filter(candidate -> candidate.combatantId().equals(userId.toString()))
                .findFirst()
                .orElseThrow();
    }

    private JsonNode heartbeat(Instant firstSeenAt, Instant secondSeenAt) {
        return objectMapper.createObjectNode()
                .put(PLAYER_A.toString(), firstSeenAt.toEpochMilli())
                .put(PLAYER_B.toString(), secondSeenAt.toEpochMilli());
    }

    private StsPvpBattle battle() {
        StsPvpBattle battle = new StsPvpBattle();
        ReflectionTestUtils.setField(battle, "id", BATTLE_ID);
        battle.setFriendly(false);
        battle.setStatus(StsPvpBattleStatus.ACTIVE);
        battle.setTeams(objectMapper.createArrayNode()
                .add(objectMapper.createArrayNode().add(participant(PLAYER_A)))
                .add(objectMapper.createArrayNode().add(participant(PLAYER_B))));
        battle.setState(objectMapper.createObjectNode());
        battle.setHeartbeat(heartbeat(NOW, NOW));
        return battle;
    }

    private JsonNode participant(UUID userId) {
        return objectMapper.createObjectNode()
                .put("userId", userId.toString())
                .put("displayName", "Joueur")
                .put("department", "")
                .put("selectedCharacter", "EP");
    }

}
