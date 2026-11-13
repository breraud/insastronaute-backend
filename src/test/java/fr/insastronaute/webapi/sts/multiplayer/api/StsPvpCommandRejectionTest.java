package fr.insastronaute.webapi.sts.multiplayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.ForbiddenException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.sts.combat.api.dto.CombatCommandEnvelope;
import fr.insastronaute.webapi.sts.combat.api.dto.CombatMessageEnvelope;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatBroadcaster;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatService;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpTestStates;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattle;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpBattleCommandRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpBattleRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import fr.insastronaute.webapi.sts.combat.command.RejectionCode;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCommandRejectedException;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * What a player is told when their PvP command is not played.
 *
 * <p>Nothing was told at all: the PvP socket had no {@code COMMAND_REJECTED}, so a command the
 * server threw out died in silence and the client sat on the revision it sent, waiting for an
 * answer that was never coming. A player out of energy has no other move to make, and the fight
 * stopped there. PvE learnt this the week before; this is the same shape on the PvP channel.
 *
 * <p>The refusal is answered <em>and then rethrown</em>. The rethrow is not decoration: the
 * handler is {@code @Transactional}, and swallowing the failure would commit whatever half-applied
 * work the failed command left behind.
 *
 * <p>The refusal itself is also a frame, and a frame is subject to the same rule as every other:
 * it carries a code and a flat sentence, never anything read off the combat. It goes to the player
 * who sent the command and to nobody else.
 */
@ExtendWith(MockitoExtension.class)
class StsPvpCommandRejectionTest {

    private static final UUID BATTLE_ID = UUID.fromString("7b1f0c34-0000-4000-8000-00000000000c");
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
                new StsPvpCombatBroadcaster(combatService, messagingTemplate, objectMapper));
    }

    @Test
    void aCommandPlayedOutOfTurnIsAnsweredToItsSender() {
        StsPvpBattle battle = battle();
        CombatState state = liveCombat();
        theBattleIsReachable(battle);
        theCommandIsNew();
        when(combatService.currentState(battle)).thenReturn(state);
        when(combatService.applyCommand(eq(battle), eq(PLAYER_A), any()))
                .thenThrow(new ForbiddenException("Ce n'est pas votre tour."));

        assertThatThrownBy(() -> controller.receiveCommand(
                BATTLE_ID.toString(), endTurn(state.revision()), principal))
                .as("the transaction only rolls back if the failure leaves the handler")
                .isInstanceOf(ForbiddenException.class);

        CombatMessageEnvelope refusal = theOnlyFrameSentTo(SUB_A);
        assertThat(refusal.type()).isEqualTo("COMMAND_REJECTED");
        assertThat(refusal.causationActionId())
                .as("without it the client cannot tell which of its commands died")
                .isEqualTo(ACTION_ID);
        assertThat(refusal.combatId()).isEqualTo(BATTLE_ID.toString());
        assertThat(reasonOf(refusal)).containsEntry("code", "NOT_ACTOR_TURN");
    }

    @Test
    void aStaleRevisionIsAnsweredRatherThanIgnored() {
        StsPvpBattle battle = battle();
        CombatState state = liveCombat();
        theBattleIsReachable(battle);
        theCommandIsNew();
        when(combatService.currentState(battle)).thenReturn(state);
        when(combatService.applyCommand(eq(battle), eq(PLAYER_A), any()))
                .thenThrow(new ConflictException("Révision de combat obsolète."));

        // The revision guard itself lives in the service, which is what is made to refuse here;
        // what is under test is that the controller turns that refusal into an answer.
        assertThatThrownBy(() -> controller.receiveCommand(
                BATTLE_ID.toString(), endTurn(state.revision()), principal))
                .isInstanceOf(ConflictException.class);

        assertThat(reasonOf(theOnlyFrameSentTo(SUB_A)))
                .as("a client behind the server has to be told to ask again")
                .containsEntry("code", "OUT_OF_SYNC");
    }

    @Test
    void anUnknownBattleIsAnsweredToo() {
        when(userRepository.findByKeycloakSub(SUB_A)).thenReturn(Optional.of(user(PLAYER_A, SUB_A)));
        when(battleRepository.findByIdWithLock(BATTLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.receiveCommand(BATTLE_ID.toString(), endTurn(0L), principal))
                .isInstanceOf(NotFoundException.class);

        assertThat(reasonOf(theOnlyFrameSentTo(SUB_A))).containsEntry("code", "COMBAT_NOT_FOUND");
    }

    @Test
    void aMalformedCommandIsAnsweredBeforeTheBattleIsEvenRead() {
        CombatCommandEnvelope elsewhere = new CombatCommandEnvelope(
                1, ACTION_ID, UUID.randomUUID().toString(), "0", "END_TURN", Map.of());

        assertThatThrownBy(() -> controller.receiveCommand(BATTLE_ID.toString(), elsewhere, principal))
                .isInstanceOf(BadRequestException.class);

        assertThat(reasonOf(theOnlyFrameSentTo(SUB_A))).containsEntry("code", "INVALID_COMMAND");
        verifyNoInteractions(battleRepository);
    }

    /**
     * A command the client never gave a name to is a command it is not waiting on: there is
     * nothing for it to match an answer against, so no answer is sent.
     */
    @Test
    void aCommandWithoutAnActionIdentifierIsNotAnswered() {
        CombatCommandEnvelope anonymous = new CombatCommandEnvelope(
                1, "  ", BATTLE_ID.toString(), "0", "END_TURN", Map.of());

        assertThatThrownBy(() -> controller.receiveCommand(BATTLE_ID.toString(), anonymous, principal))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(messagingTemplate);
    }

    /**
     * The engine's own reason reaches the player, rather than being flattened on the way.
     *
     * <p>PvP threw a bare BadRequestException for every engine refusal, and the socket layer,
     * which reads the failure's type, had nothing left to read: not enough energy, a card no
     * longer in hand and an impossible target all arrived as INVALID_COMMAND. On a channel where
     * the player cannot see the server's logs, that is the difference between knowing what to do
     * next and guessing.
     */
    @Test
    void theEngineSOwnReasonReachesThePlayer() {
        Map<String, String> payload = StsPvpCombatSocketController.refusalReasonFor(
                new StsPvpCommandRejectedException(
                        RejectionCode.INSUFFICIENT_ENERGY, "Actor energy is below card cost 3"));

        assertThat(payload.get("code")).isEqualTo("INSUFFICIENT_ENERGY");
    }

    @Test
    void aRefusalWithNoEngineCodeStillSaysSomethingUsable() {
        Map<String, String> payload = StsPvpCombatSocketController.refusalReasonFor(
                new BadRequestException("Cette commande n'est pas émise au nom de son joueur."));

        assertThat(payload.get("code"))
                .as("a refusal the engine never saw keeps the generic code it always had")
                .isEqualTo("INVALID_COMMAND");
    }

    @Test
    void theOpponentIsToldNothingAndNeitherIsAnybodyElse() {
        StsPvpBattle battle = battle();
        CombatState state = liveCombat();
        theBattleIsReachable(battle);
        theCommandIsNew();
        when(combatService.currentState(battle)).thenReturn(state);
        when(combatService.applyCommand(eq(battle), eq(PLAYER_A), any()))
                .thenThrow(new ForbiddenException("Ce n'est pas votre tour."));

        assertThatThrownBy(() -> controller.receiveCommand(
                BATTLE_ID.toString(), endTurn(state.revision()), principal))
                .isInstanceOf(ForbiddenException.class);

        verify(messagingTemplate).convertAndSendToUser(eq(SUB_A), eq(DESTINATION), any(Object.class));
        verifyNoMoreInteractions(messagingTemplate);
    }

    /**
     * The refusal is not a back door into the combat.
     *
     * <p>It is built from the exception's type alone, never from its message and never from the
     * state: the operator detail is in the log, where it cannot be read by a player.
     */
    @Test
    void theRefusalCarriesNothingOfTheCombat() throws Exception {
        StsPvpBattle battle = battle();
        CombatState state = liveCombat();
        theBattleIsReachable(battle);
        theCommandIsNew();
        when(combatService.currentState(battle)).thenReturn(state);
        when(combatService.applyCommand(eq(battle), eq(PLAYER_A), any()))
                .thenThrow(new ConflictException(
                        "obsolète, la main adverse tient " + handOf(state, PLAYER_B).getFirst()));

        assertThatThrownBy(() -> controller.receiveCommand(
                BATTLE_ID.toString(), endTurn(state.revision()), principal))
                .isInstanceOf(ConflictException.class);

        String sent = objectMapper.writeValueAsString(theOnlyFrameSentTo(SUB_A));
        assertThat(reasonOf(theOnlyFrameSentTo(SUB_A))).containsOnlyKeys("code", "message");
        assertThat(sent)
                .as("an exception message is operator material and may quote the combat")
                .doesNotContain(handOf(state, PLAYER_B).toArray(String[]::new))
                .doesNotContain(handOf(state, PLAYER_A).toArray(String[]::new))
                .doesNotContain(String.valueOf(state.random().value()));
    }

    // ------------------------------------------------------------------------ fixtures

    private CombatMessageEnvelope theOnlyFrameSentTo(String keycloakSub) {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate)
                .convertAndSendToUser(eq(keycloakSub), eq(DESTINATION), payload.capture());
        return (CombatMessageEnvelope) payload.getValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> reasonOf(CombatMessageEnvelope frame) {
        return (Map<String, String>) frame.payload();
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
}
