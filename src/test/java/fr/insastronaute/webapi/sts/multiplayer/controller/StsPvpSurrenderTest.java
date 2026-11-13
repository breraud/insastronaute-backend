package fr.insastronaute.webapi.sts.multiplayer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.auth.UserSynchronizationService;
import fr.insastronaute.webapi.card.service.CardService;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.sts.combat.api.dto.CombatMessageEnvelope;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatBroadcaster;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatService;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpTestStates;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattle;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattleStatus;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpProfile;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpBattleDto;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpBattleRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpChallengeRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpDeckRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpNotificationRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpProfileRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpQueueEntryRepository;
import fr.insastronaute.webapi.sts.multiplayer.service.StsPvpService;
import fr.insastronaute.webapi.sts.repository.StsRunRepository;
import fr.insastronaute.webapi.sts.service.StsGameDataService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Giving up on purpose.
 *
 * <p>Until now the only way a PvP duel could end short of a kill was the timeout sweep deciding a
 * player had left: {@code StsPvpBattleSettlement.conceded} had exactly one caller, and no client
 * could reach it. A player who wanted out had to close the tab and wait two minutes for the grace
 * period to run out, which looks to the other player exactly like a crash.
 *
 * <p>What is under test is the decision and what is announced, not the engine. The broadcaster is
 * the real one over a mocked messaging template, because "the opponent is told the duel is over"
 * is the half that actually matters and a mocked broadcaster would only pin that a method was
 * called. The two players sit on their own user queues, so a {@code CombatEnded} that reaches one
 * of them is not evidence it reached the other.
 */
class StsPvpSurrenderTest {

    private static final UUID BATTLE_ID = UUID.fromString("aaaaaaaa-0000-4000-8000-00000000000d");
    private static final UUID PLAYER_A = UUID.fromString("11111111-0000-4000-8000-000000000001");
    private static final UUID PLAYER_B = UUID.fromString("22222222-0000-4000-8000-000000000002");
    private static final UUID STRANGER = UUID.fromString("33333333-0000-4000-8000-000000000003");
    private static final String SUB_A = "sub-a";
    private static final String SUB_B = "sub-b";
    private static final String SUB_STRANGER = "sub-stranger";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final StsPvpBattleRepository battleRepository = mock(StsPvpBattleRepository.class);
    private final StsPvpDeckRepository deckRepository = mock(StsPvpDeckRepository.class);
    private final StsPvpProfileRepository profileRepository = mock(StsPvpProfileRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final StsGameDataService gameDataService = mock(StsGameDataService.class);
    private final UserSynchronizationService synchronizationService =
            mock(UserSynchronizationService.class);
    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);

    private final StsPvpCombatService combatService = new StsPvpCombatService(
            objectMapper, gameDataService, deckRepository, profileRepository, userRepository,
            mock(AutoChallengeTriggerPublisher.class), List.of(),
            new fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentEngine(List.of()));

    private final StsPvpCombatBroadcaster broadcaster =
            new StsPvpCombatBroadcaster(combatService, messagingTemplate, objectMapper);

    private final StsPvpService service = new StsPvpService(
            profileRepository,
            deckRepository,
            mock(StsPvpQueueEntryRepository.class),
            mock(StsPvpChallengeRepository.class),
            battleRepository,
            mock(StsPvpNotificationRepository.class),
            userRepository,
            mock(fr.insastronaute.webapi.friend.repository.FriendshipRepository.class),
            mock(DepartmentRepository.class),
            mock(CardService.class),
            gameDataService,
            mock(StsRunRepository.class),
            objectMapper,
            combatService,
            broadcaster,
            mock(fr.insastronaute.webapi.card.service.CollectionTestModeService.class));

    private final StsPvpController controller = new StsPvpController(synchronizationService, service);

    private final OidcUser oidcUser = mock(OidcUser.class);

    /** The whole point: the duel is over and the other player won it. */
    @Test
    void theSurrenderEndsTheDuelAndTheOpponentWinsIt() {
        StsPvpBattle battle = battleHolding(liveCombat());
        theBattleIsSurrenderedBy(battle, PLAYER_A);
        bothPlayersHaveAProfile();

        StsPvpBattleDto answer = controller.surrenderBattle(oidcUser, BATTLE_ID);

        assertThat(battle.getStatus()).isEqualTo(StsPvpBattleStatus.FINISHED);
        assertThat(battle.getWinnerTeam())
                .as("the player still sitting there is on team-1")
                .isEqualTo(1);
        assertThat(battle.getTurnDeadline())
                .as("a finished duel has no turn left to run out")
                .isNull();
        assertThat(answer.status()).isEqualTo("FINISHED");
        verify(battleRepository).save(battle);

        // The combat itself is written off too, so a player who reconnects cannot keep playing a
        // duel they gave up on.
        CombatState stored = combatService.currentState(battle);
        assertThat(stored.status()).isEqualTo(CombatStatus.FINISHED);
        assertThat(stored.winnerTeamId()).isEqualTo("team-1");
        assertThat(hpOf(stored, PLAYER_A)).isZero();
        assertThat(hpOf(stored, PLAYER_B)).isPositive();
    }

    /**
     * Both players are told, each on their own queue.
     *
     * <p>The client reads the outcome from the event and not from the state, so a duel that ends
     * without a {@code CombatEnded} leaves a screen showing a fight still under way — and the
     * screen that matters most here is the one belonging to the player who did not press anything.
     */
    @Test
    void aCombatEndedReachesBothPlayers() {
        StsPvpBattle battle = battleHolding(liveCombat());
        theBattleIsSurrenderedBy(battle, PLAYER_A);
        bothPlayersHaveAProfile();

        controller.surrenderBattle(oidcUser, BATTLE_ID);

        assertThat(whoWasToldTheCombatEnded())
                .as("the opponent learns the duel is over from the event or not at all")
                .containsExactlyInAnyOrder(SUB_A, SUB_B);
        assertThat(theCombatEndedFrames())
                .allSatisfy(frame -> assertThat(frame.get("winnerTeamId")).isEqualTo("team-1"));
    }

    /** Conceding is losing: the rating moves exactly as it does when a player simply vanishes. */
    @Test
    void concedingCostsTheRatingTheSameWayAnAbsenceDoes() {
        StsPvpBattle battle = battleHolding(liveCombat());
        theBattleIsSurrenderedBy(battle, PLAYER_A);
        StsPvpProfile quitter = profile();
        StsPvpProfile opponent = profile();
        when(profileRepository.findByUser_KeycloakSub(SUB_A)).thenReturn(Optional.of(quitter));
        when(profileRepository.findByUser_KeycloakSub(SUB_B)).thenReturn(Optional.of(opponent));

        controller.surrenderBattle(oidcUser, BATTLE_ID);

        assertThat(opponent.getElo()).isEqualTo(1016);
        assertThat(quitter.getElo()).isEqualTo(984);
        assertThat(opponent.getWinsRanked()).isEqualTo(1);
        assertThat(quitter.getLossesRanked()).isEqualTo(1);
        verify(profileRepository).save(opponent);
        verify(profileRepository).save(quitter);
    }

    /** A stranger cannot end two other people's duel. */
    @Test
    void aStrangerToTheBattleCannotEndIt() {
        StsPvpBattle battle = battleHolding(liveCombat());
        theBattleIsSurrenderedBy(battle, STRANGER);

        assertThatThrownBy(() -> controller.surrenderBattle(oidcUser, BATTLE_ID))
                .isInstanceOf(BadRequestException.class);

        assertThat(battle.getStatus()).isEqualTo(StsPvpBattleStatus.ACTIVE);
        assertThat(battle.getWinnerTeam()).isNull();
        verify(battleRepository, never()).save(any());
        verify(profileRepository, never()).save(any());
        verifyNoInteractions(messagingTemplate);
    }

    /**
     * And the refusal says nothing about the duel it refused.
     *
     * <p>An unauthenticated peek at a combat is worth more than the refusal is worth denying: the
     * stored state holds both hands and the seed every future draw replays from, so a message that
     * quoted any of it would turn "you are not in this battle" into a way to read it.
     */
    @Test
    void theRefusalCarriesNothingOfTheCombat() {
        CombatState state = liveCombat();
        StsPvpBattle battle = battleHolding(state);
        theBattleIsSurrenderedBy(battle, STRANGER);

        assertThatThrownBy(() -> controller.surrenderBattle(oidcUser, BATTLE_ID))
                .isInstanceOf(BadRequestException.class)
                .satisfies(refusal -> {
                    String message = refusal.getMessage();
                    assertThat(message).doesNotContain(String.valueOf(state.random().value()));
                    for (String instanceId : handInstanceIds(state, PLAYER_A)) {
                        assertThat(message).doesNotContain(instanceId);
                    }
                    for (String instanceId : handInstanceIds(state, PLAYER_B)) {
                        assertThat(message).doesNotContain(instanceId);
                    }
                });
    }

    @Test
    void anUnknownBattleCannotBeSurrendered() {
        when(synchronizationService.syncWithKeycloak(oidcUser)).thenReturn(user(PLAYER_A, SUB_A));
        when(battleRepository.findByIdWithLock(BATTLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.surrenderBattle(oidcUser, BATTLE_ID))
                .isInstanceOf(NotFoundException.class);
    }

    /** A duel already settled takes no second surrender: the rating has moved once and stays moved. */
    @Test
    void surrenderingABattleAlreadySettledDoesNothing() {
        StsPvpBattle battle = battleHolding(liveCombat());
        battle.setStatus(StsPvpBattleStatus.FINISHED);
        battle.setWinnerTeam(0);
        theBattleIsSurrenderedBy(battle, PLAYER_A);

        StsPvpBattleDto answer = controller.surrenderBattle(oidcUser, BATTLE_ID);

        assertThat(answer.status()).isEqualTo("FINISHED");
        assertThat(battle.getWinnerTeam())
                .as("the winner of a settled duel is not rewritten by its loser giving up after the fact")
                .isEqualTo(0);
        verify(profileRepository, never()).save(any());
        verifyNoInteractions(messagingTemplate);
    }

    /**
     * And a combat the engine already ended is left exactly as it is.
     *
     * <p>{@code conceded} guards this itself — it hands back the state untouched and nothing to
     * announce when the combat is no longer ACTIVE — and this pins that the surrender path leans
     * on that guard rather than rewriting the outcome over it.
     */
    @Test
    void surrenderingACombatTheEngineAlreadyEndedChangesNothing() {
        CombatState finished = StsPvpTestStates.secondTeamDefeated(
                PLAYER_A.toString(), PLAYER_B.toString());
        StsPvpBattle battle = battleHolding(finished);
        theBattleIsSurrenderedBy(battle, PLAYER_A);

        controller.surrenderBattle(oidcUser, BATTLE_ID);

        CombatState stored = combatService.currentState(battle);
        assertThat(stored.winnerTeamId())
                .as("team-0 won this combat on the table; giving up afterwards does not undo it")
                .isEqualTo("team-0");
        assertThat(hpOf(stored, PLAYER_A)).isPositive();
        verify(profileRepository, never()).save(any());
        verifyNoInteractions(messagingTemplate);
    }

    // ------------------------------------------------------------------------ fixtures

    private void theBattleIsSurrenderedBy(StsPvpBattle battle, UUID caller) {
        String sub = SUB_A;
        if (PLAYER_B.equals(caller)) {
            sub = SUB_B;
        } else if (STRANGER.equals(caller)) {
            sub = SUB_STRANGER;
        }
        when(synchronizationService.syncWithKeycloak(oidcUser)).thenReturn(user(caller, sub));
        when(battleRepository.findByIdWithLock(BATTLE_ID)).thenReturn(Optional.of(battle));
        when(userRepository.findById(PLAYER_A)).thenReturn(Optional.of(user(PLAYER_A, SUB_A)));
        when(userRepository.findById(PLAYER_B)).thenReturn(Optional.of(user(PLAYER_B, SUB_B)));
    }

    private void bothPlayersHaveAProfile() {
        when(profileRepository.findByUser_KeycloakSub(SUB_A)).thenReturn(Optional.of(profile()));
        when(profileRepository.findByUser_KeycloakSub(SUB_B)).thenReturn(Optional.of(profile()));
    }

    private static CombatState liveCombat() {
        return StsPvpTestStates.twoHumans(PLAYER_A.toString(), PLAYER_B.toString());
    }

    private StsPvpBattle battleHolding(CombatState state) {
        StsPvpBattle battle = new StsPvpBattle();
        ReflectionTestUtils.setField(battle, "id", BATTLE_ID);
        battle.setFriendly(false);
        battle.setStatus(StsPvpBattleStatus.ACTIVE);
        battle.setTurnDeadline(java.time.Instant.parse("2026-08-24T12:00:30Z"));
        battle.setTeams(teams());
        battle.setState(objectMapper.valueToTree(state));
        battle.setHeartbeat(objectMapper.createObjectNode());
        return battle;
    }

    private ArrayNode teams() {
        ArrayNode teams = objectMapper.createArrayNode();
        for (UUID userId : List.of(PLAYER_A, PLAYER_B)) {
            teams.addArray().addObject()
                    .put("userId", userId.toString())
                    .put("displayName", "Joueur")
                    .put("department", "")
                    .put("selectedCharacter", "EP");
        }
        return teams;
    }

    /** The subs a {@code CombatEnded} frame was actually addressed to. */
    private List<String> whoWasToldTheCombatEnded() {
        ArgumentCaptor<String> recipients = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> frames = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, atLeastOnce())
                .convertAndSendToUser(recipients.capture(), anyString(), frames.capture());

        List<String> told = new ArrayList<>();
        for (int index = 0; index < frames.getAllValues().size(); index++) {
            if (combatEndedPayload(frames.getAllValues().get(index)) != null) {
                told.add(recipients.getAllValues().get(index));
            }
        }
        return told;
    }

    private List<Map<String, Object>> theCombatEndedFrames() {
        ArgumentCaptor<Object> frames = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, atLeastOnce())
                .convertAndSendToUser(anyString(), anyString(), frames.capture());
        return frames.getAllValues().stream()
                .map(StsPvpSurrenderTest::combatEndedPayload)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> combatEndedPayload(Object frame) {
        if (!(frame instanceof CombatMessageEnvelope envelope)
                || !"COMBAT_EVENT".equals(envelope.type())
                || !(envelope.payload() instanceof Map<?, ?> payload)
                || !"CombatEnded".equals(payload.get("eventType"))) {
            return null;
        }
        return (Map<String, Object>) payload;
    }

    private static int hpOf(CombatState state, UUID userId) {
        return state.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(userId.toString()))
                .findFirst()
                .orElseThrow()
                .hp();
    }

    private static List<String> handInstanceIds(CombatState state, UUID userId) {
        return state.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(userId.toString()))
                .findFirst()
                .orElseThrow()
                .piles().hand().stream()
                .map(card -> card.instanceId())
                .toList();
    }

    private static AppUser user(UUID id, String keycloakSub) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setKeycloakSub(keycloakSub);
        return user;
    }

    private static StsPvpProfile profile() {
        StsPvpProfile profile = new StsPvpProfile();
        profile.setElo(1000);
        profile.setWinsRanked(0);
        profile.setLossesRanked(0);
        profile.setWinsFriendly(0);
        profile.setLossesFriendly(0);
        return profile;
    }
}
