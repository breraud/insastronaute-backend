package fr.insastronaute.webapi.sts.multiplayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.sts.combat.api.dto.CombatCommandEnvelope;
import fr.insastronaute.webapi.sts.combat.api.dto.CombatMessageEnvelope;
import fr.insastronaute.webapi.sts.combat.event.CardDrawn;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.DamageApplied;
import fr.insastronaute.webapi.sts.combat.command.CombatCommand;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatBroadcaster;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatService;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatService.StsPvpCommandOutcome;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpSeat;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatView;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpTestStates;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattle;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattleCommand;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpBattleCommandRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpBattleRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * The socket end of an authoritative PvP combat.
 *
 * <p>What is under test is the one thing a shared broadcast could never do: two players sitting
 * in the same combat are sent two different states, each one holding its reader's hand and
 * nothing of the other's. The projection itself is tested elsewhere; here it is the delivery
 * that must not put the wrong state in the wrong queue.
 */
@ExtendWith(MockitoExtension.class)
class StsPvpCombatSocketControllerTest {

    private static final UUID BATTLE_ID = UUID.fromString("7b1f0c34-0000-4000-8000-00000000000b");
    private static final UUID PLAYER_A = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PLAYER_B = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String SUB_A = "sub-a";
    private static final String SUB_B = "sub-b";
    private static final String DESTINATION = "/queue/sts/pvp/battles/" + BATTLE_ID;
    private static final String ACTION_ID = "action-1";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock private StsPvpBattleRepository battleRepository;
    @Mock private StsPvpBattleCommandRepository commandRepository;
    @Mock private StsPvpCombatService combatService;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private StsPvpCombatSocketController controller;

    private final Principal principal = () -> SUB_A;

    @BeforeEach
    void setUp() {
        controller = new StsPvpCombatSocketController(
                battleRepository,
                commandRepository,
                combatService,
                userRepository,
                // The delivery is real: what these tests are about is what lands in which queue.
                new StsPvpCombatBroadcaster(combatService, messagingTemplate, objectMapper));
    }

    @Test
    void eachPlayerReceivesItsOwnProjectedView() throws Exception {
        StsPvpBattle battle = battle();
        CombatState state = liveCombat();
        theBattleIsReachable(battle);
        theCommandIsNew();
        when(combatService.currentState(battle)).thenReturn(state);
        when(combatService.applyCommand(eq(battle), eq(PLAYER_A), any()))
                .thenReturn(new StsPvpCommandOutcome(state, List.of(), false));
        when(combatService.playerSeats(battle)).thenReturn(participants());

        controller.receiveCommand(BATTLE_ID.toString(), endTurn(state.revision()), principal);

        ArgumentCaptor<CombatCommand> command = ArgumentCaptor.forClass(CombatCommand.class);
        verify(combatService).applyCommand(eq(battle), eq(PLAYER_A), command.capture());
        assertThat(command.getValue()).isInstanceOf(EndTurnCommand.class);
        assertThat(command.getValue().actorId()).isEqualTo(PLAYER_A.toString());
        assertThat(command.getValue().combatId()).isEqualTo(state.combatId());
        assertThat(command.getValue().expectedRevision()).isEqualTo(state.revision());

        ArgumentCaptor<String> recipient = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> destination = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, times(2))
                .convertAndSendToUser(recipient.capture(), destination.capture(), payload.capture());

        assertThat(recipient.getAllValues()).containsExactly(SUB_A, SUB_B);
        assertThat(destination.getAllValues()).containsOnly(DESTINATION);

        String sentToA = objectMapper.writeValueAsString(payload.getAllValues().get(0));
        String sentToB = objectMapper.writeValueAsString(payload.getAllValues().get(1));

        // A reads its own hand; the very same cards are absent from what B is sent.
        assertThat(handOf(state, PLAYER_A)).isNotEmpty();
        assertThat(sentToA).contains(handOf(state, PLAYER_A).toArray(String[]::new));
        assertThat(sentToB).doesNotContain(handOf(state, PLAYER_A).toArray(String[]::new));
        // and symmetrically, A is told nothing of B's hand.
        assertThat(sentToB).contains(handOf(state, PLAYER_B).toArray(String[]::new));
        assertThat(sentToA).doesNotContain(handOf(state, PLAYER_B).toArray(String[]::new));

        // The seed the whole future draw order can be replayed from never travels either.
        assertThat(sentToA).doesNotContain(String.valueOf(state.random().value()));
        assertThat(sentToB).doesNotContain(String.valueOf(state.random().value()));

        assertThat(viewIn(payload.getAllValues().get(0), PLAYER_A).piles()).isNotNull();
        assertThat(viewIn(payload.getAllValues().get(0), PLAYER_B).piles()).isNull();
        assertThat(viewIn(payload.getAllValues().get(1), PLAYER_B).piles()).isNotNull();
        assertThat(viewIn(payload.getAllValues().get(1), PLAYER_A).piles()).isNull();
    }

    @Test
    void aCommandAddressedToAnotherBattleIsRefused() {
        CombatCommandEnvelope elsewhere = new CombatCommandEnvelope(
                1, ACTION_ID, UUID.randomUUID().toString(), "0", "END_TURN", Map.of());

        assertThatThrownBy(() -> controller.receiveCommand(BATTLE_ID.toString(), elsewhere, principal))
                .isInstanceOf(BadRequestException.class);

        verify(combatService, never()).applyCommand(any(), any(), any());
        verifyNoInteractions(battleRepository);
        // Refused is not the same as ignored: the sender is told, or it waits for ever.
        assertThat(refusalSentTo(SUB_A).type()).isEqualTo("COMMAND_REJECTED");
    }

    @Test
    void aSecondDeliveryOfTheSameCommandIsAnsweredWithoutPlayingItAgain() {
        StsPvpBattle battle = battle();
        CombatState state = liveCombat();
        theBattleIsReachable(battle);
        when(commandRepository.findByBattleIdAndActingUserIdAndActionId(BATTLE_ID, PLAYER_A, ACTION_ID))
                .thenReturn(Optional.of(StsPvpBattleCommand.accepted(
                        BATTLE_ID, PLAYER_A, ACTION_ID, state.revision())));
        when(combatService.currentState(battle)).thenReturn(state);

        controller.receiveCommand(BATTLE_ID.toString(), endTurn(state.revision()), principal);

        verify(combatService, never()).applyCommand(any(), any(), any());
        verify(commandRepository, never()).save(any());
        // Only the player whose delivery was repeated is answered: the other one already has it.
        verify(messagingTemplate).convertAndSendToUser(eq(SUB_A), eq(DESTINATION), any(Object.class));
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    void anAcceptedCommandIsRememberedSoItCannotBePlayedTwice() {
        StsPvpBattle battle = battle();
        CombatState state = liveCombat();
        theBattleIsReachable(battle);
        theCommandIsNew();
        when(combatService.currentState(battle)).thenReturn(state);
        when(combatService.applyCommand(eq(battle), eq(PLAYER_A), any()))
                .thenReturn(new StsPvpCommandOutcome(state, List.of(), false));
        when(combatService.playerSeats(battle)).thenReturn(participants());

        controller.receiveCommand(BATTLE_ID.toString(), endTurn(state.revision()), principal);

        ArgumentCaptor<StsPvpBattleCommand> remembered =
                ArgumentCaptor.forClass(StsPvpBattleCommand.class);
        verify(commandRepository).save(remembered.capture());
        assertThat(remembered.getValue().getBattleId()).isEqualTo(BATTLE_ID);
        assertThat(remembered.getValue().getActingUserId()).isEqualTo(PLAYER_A);
        assertThat(remembered.getValue().getActionId()).isEqualTo(ACTION_ID);
        assertThat(remembered.getValue().getResultingRevision()).isEqualTo(state.revision());
    }

    @Test
    void aPlayedCardCarriesItsTargetsToTheEngine() {
        StsPvpBattle battle = battle();
        CombatState state = liveCombat();
        theBattleIsReachable(battle);
        theCommandIsNew();
        when(combatService.currentState(battle)).thenReturn(state);
        when(combatService.applyCommand(eq(battle), eq(PLAYER_A), any()))
                .thenReturn(new StsPvpCommandOutcome(state, List.of(), false));
        when(combatService.playerSeats(battle)).thenReturn(participants());

        String cardInstanceId = handOf(state, PLAYER_A).getFirst();
        CombatCommandEnvelope playCard = new CombatCommandEnvelope(
                1,
                ACTION_ID,
                BATTLE_ID.toString(),
                String.valueOf(state.revision()),
                "PLAY_CARD",
                Map.of("cardInstanceId", cardInstanceId, "targetIds", List.of(PLAYER_B.toString())));

        controller.receiveCommand(BATTLE_ID.toString(), playCard, principal);

        ArgumentCaptor<CombatCommand> command = ArgumentCaptor.forClass(CombatCommand.class);
        verify(combatService).applyCommand(eq(battle), eq(PLAYER_A), command.capture());
        assertThat(command.getValue()).isInstanceOf(PlayCardCommand.class);
        PlayCardCommand played = (PlayCardCommand) command.getValue();
        assertThat(played.cardInstanceId()).isEqualTo(cardInstanceId);
        assertThat(played.targetIds()).containsExactly(PLAYER_B.toString());
        assertThat(played.actorId()).isEqualTo(PLAYER_A.toString());
    }

    @Test
    void aCommandWithoutAnActionIdentifierIsRefused() {
        CombatCommandEnvelope anonymous = new CombatCommandEnvelope(
                1, "  ", BATTLE_ID.toString(), "0", "END_TURN", Map.of());

        assertThatThrownBy(() -> controller.receiveCommand(BATTLE_ID.toString(), anonymous, principal))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(battleRepository, messagingTemplate);
    }

    @Test
    void anUnsupportedProtocolVersionIsRefused() {
        CombatCommandEnvelope future = new CombatCommandEnvelope(
                2, ACTION_ID, BATTLE_ID.toString(), "0", "END_TURN", Map.of());

        assertThatThrownBy(() -> controller.receiveCommand(BATTLE_ID.toString(), future, principal))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(battleRepository);
        assertThat(refusalSentTo(SUB_A).type()).isEqualTo("COMMAND_REJECTED");
    }

    @Test
    void aCommandForAnUnknownBattleIsRefused() {
        when(userRepository.findByKeycloakSub(SUB_A)).thenReturn(Optional.of(user(PLAYER_A, SUB_A)));
        when(battleRepository.findByIdWithLock(BATTLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.receiveCommand(
                BATTLE_ID.toString(), endTurn(0L), principal))
                .isInstanceOf(NotFoundException.class);
        verify(combatService, never()).applyCommand(any(), any(), any());
        assertThat(refusalSentTo(SUB_A).type()).isEqualTo("COMMAND_REJECTED");
    }

    /** The single frame {@code keycloakSub} was sent, which a refusal answers them with. */
    private CombatMessageEnvelope refusalSentTo(String keycloakSub) {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate)
                .convertAndSendToUser(eq(keycloakSub), eq(DESTINATION), payload.capture());
        return (CombatMessageEnvelope) payload.getValue();
    }

    private void theBattleIsReachable(StsPvpBattle battle) {
        when(userRepository.findByKeycloakSub(SUB_A)).thenReturn(Optional.of(user(PLAYER_A, SUB_A)));
        when(battleRepository.findByIdWithLock(BATTLE_ID)).thenReturn(Optional.of(battle));
    }

    private void theCommandIsNew() {
        when(commandRepository.findByBattleIdAndActingUserIdAndActionId(BATTLE_ID, PLAYER_A, ACTION_ID))
                .thenReturn(Optional.empty());
    }

    private CombatCommandEnvelope endTurn(long expectedRevision) {
        return new CombatCommandEnvelope(
                1, ACTION_ID, BATTLE_ID.toString(), String.valueOf(expectedRevision), "END_TURN", Map.of());
    }

    private static CombatState liveCombat() {
        return StsPvpTestStates.twoHumans(PLAYER_A.toString(), PLAYER_B.toString());
    }

    private static List<StsPvpSeat> participants() {
        return List.of(
                StsPvpSeat.human(PLAYER_A, SUB_A, "A", "EP", 0),
                StsPvpSeat.human(PLAYER_B, SUB_B, "B", "EP", 1));
    }

    private static List<String> handOf(CombatState state, UUID userId) {
        return state.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(userId.toString()))
                .findFirst()
                .map(CombatantState::piles)
                .orElseThrow()
                .hand().stream()
                .map(card -> card.instanceId())
                .toList();
    }

    private static StsPvpCombatView.StsPvpCombatantView viewIn(Object frame, UUID userId) {
        StsPvpCombatView view = (StsPvpCombatView) ((CombatMessageEnvelope) frame).payload();
        return view.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(userId.toString()))
                .findFirst()
                .orElseThrow();
    }

    private static StsPvpBattle battle() {
        StsPvpBattle battle = new StsPvpBattle();
        ReflectionTestUtils.setField(battle, "id", BATTLE_ID);
        return battle;
    }

    private static AppUser user(UUID id, String keycloakSub) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setKeycloakSub(keycloakSub);
        return user;
    }

    /**
     * The engine's events reach the players, each one only what it may see.
     *
     * <p>They were produced and dropped: the client had nothing to animate and a PvP combat
     * jumped from state to state. What travels is filtered per player and sent before the state,
     * because an animation delivered after the state it explains has nothing left to play.
     */
    @Test
    void theEventsTravelAheadOfTheStateAndOnlyToWhoMaySeeThem() {
        StsPvpBattle battle = battle();
        CombatState state = liveCombat();
        theBattleIsReachable(battle);
        theCommandIsNew();
        when(combatService.currentState(battle)).thenReturn(state);
        String drawnByA = handOf(state, PLAYER_A).getFirst();
        List<CombatEvent> events = List.of(
                new DamageApplied(
                        state.combatId(), state.revision(),
                        PLAYER_A.toString(), PLAYER_B.toString(), 6, 0, 6, 0, 19),
                new CardDrawn(
                        state.combatId(), state.revision(), PLAYER_A.toString(), drawnByA, "strike", 0));
        when(combatService.applyCommand(eq(battle), eq(PLAYER_A), any()))
                .thenReturn(new StsPvpCommandOutcome(state, events, false));
        when(combatService.playerSeats(battle)).thenReturn(participants());

        controller.receiveCommand(BATTLE_ID.toString(), endTurn(state.revision()), principal);

        ArgumentCaptor<String> recipient = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, times(5))
                .convertAndSendToUser(recipient.capture(), eq(DESTINATION), payload.capture());

        List<CombatMessageEnvelope> frames = payload.getAllValues().stream()
                .map(CombatMessageEnvelope.class::cast)
                .toList();
        assertThat(frames.stream().map(CombatMessageEnvelope::type))
                .containsExactly("COMBAT_EVENT", "COMBAT_EVENT", "COMBAT_EVENT",
                        "STATE_UPDATED", "STATE_UPDATED");
        // A is told of the damage and of its own draw; B only of the damage.
        assertThat(recipient.getAllValues())
                .containsExactly(SUB_A, SUB_A, SUB_B, SUB_A, SUB_B);

        // The client dispatches on eventType, and a frame without it is one it drops in silence.
        assertThat(frames.get(0).payload()).asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("eventType", "DamageApplied");
        assertThat(frames.get(1).payload()).asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("eventType", "CardDrawn");
        assertThat(frames.get(2).payload()).asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("eventType", "DamageApplied");

        assertThat(frames.get(2).payload().toString())
                .as("the card A drew is named nowhere in what B is sent")
                .doesNotContain(drawnByA);
    }
}
