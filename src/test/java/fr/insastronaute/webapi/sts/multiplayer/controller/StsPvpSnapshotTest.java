package fr.insastronaute.webapi.sts.multiplayer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.auth.UserSynchronizationService;
import fr.insastronaute.webapi.card.service.CardService;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.sts.combat.api.dto.CombatSnapshotDto;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatBroadcaster;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatService;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatView;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpTestStates;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattle;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattleStatus;
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
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The PvP combat as a resynchronising client reads it back.
 *
 * <p>The React bridge asks {@code GET /api/sts/combats/{id}} for whatever combat it is in. That
 * endpoint is the PvE one: it answers {@code "PVE"} hard-coded and a shape {@code
 * parseCombatSnapshot} rejects, so a PvP client that lost a frame asked, failed to parse, and
 * asked again. PvP gets its own snapshot, in the shape the client already knows.
 *
 * <p>The interesting half is who is served. A PvP {@code CombatState} holds both hands and the
 * seed every future draw is computable from, so a snapshot is a projection or it is a leak: what
 * travels is {@link StsPvpCombatView#forViewer} for the <em>calling</em> player, and a player who
 * is not in this battle is refused rather than handed somebody else's.
 */
class StsPvpSnapshotTest {

    private static final UUID BATTLE_ID = UUID.fromString("aaaaaaaa-0000-4000-8000-00000000000c");
    private static final UUID PLAYER_A = UUID.fromString("11111111-0000-4000-8000-000000000001");
    private static final UUID PLAYER_B = UUID.fromString("22222222-0000-4000-8000-000000000002");
    private static final UUID STRANGER = UUID.fromString("33333333-0000-4000-8000-000000000003");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final StsPvpBattleRepository battleRepository = mock(StsPvpBattleRepository.class);
    private final StsPvpDeckRepository deckRepository = mock(StsPvpDeckRepository.class);
    private final StsPvpProfileRepository profileRepository = mock(StsPvpProfileRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final StsGameDataService gameDataService = mock(StsGameDataService.class);
    private final UserSynchronizationService synchronizationService =
            mock(UserSynchronizationService.class);

    private final StsPvpCombatService combatService = new StsPvpCombatService(
            objectMapper, gameDataService, deckRepository, profileRepository, userRepository,
            mock(AutoChallengeTriggerPublisher.class), List.of(),
            new fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentEngine(List.of()));

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
            mock(StsPvpCombatBroadcaster.class),
            mock(fr.insastronaute.webapi.card.service.CollectionTestModeService.class));

    private final StsPvpController controller = new StsPvpController(synchronizationService, service);

    private final OidcUser oidcUser = mock(OidcUser.class);

    @Test
    void theSnapshotIsTheCallersOwnViewOfTheCombat() {
        CombatState state = StsPvpTestStates.twoHumans(PLAYER_A.toString(), PLAYER_B.toString());
        theBattleIsReadBy(battleHolding(state), PLAYER_A);

        CombatSnapshotDto snapshot = controller.combatSnapshot(oidcUser, BATTLE_ID);

        assertThat(snapshot.protocolVersion()).isEqualTo(1);
        assertThat(snapshot.combatId()).isEqualTo(BATTLE_ID.toString());
        assertThat(snapshot.revision()).isEqualTo(Long.toString(state.revision()));
        assertThat(snapshot.mode())
                .as("the PvE endpoint answered PVE for a PvP combat and the client believed it")
                .isEqualTo("PVP");

        assertThat(snapshot.payload()).isInstanceOf(StsPvpCombatView.class);
        StsPvpCombatView view = (StsPvpCombatView) snapshot.payload();
        assertThat(combatantOf(view, PLAYER_A).piles())
                .as("the caller reads their own hand")
                .isNotNull();
        assertThat(combatantOf(view, PLAYER_B).piles())
                .as("and never the other one's")
                .isNull();
    }

    @Test
    void theSnapshotCarriesNeitherTheOpponentCardsNorTheSeed() throws Exception {
        CombatState state = StsPvpTestStates.twoHumans(PLAYER_A.toString(), PLAYER_B.toString());
        theBattleIsReadBy(battleHolding(state), PLAYER_A);

        String json = objectMapper.writeValueAsString(controller.combatSnapshot(oidcUser, BATTLE_ID));

        assertThat(json).contains(handInstanceIds(state, PLAYER_A.toString()));
        assertThat(json)
                .as("player B's cards are player B's alone")
                .doesNotContain(handInstanceIds(state, PLAYER_B.toString()));
        assertThat(json)
                .as("the seed replays every future draw of both players")
                .doesNotContain("random")
                .doesNotContain(String.valueOf(state.random().value()));
    }

    @Test
    void theDeadlineTravelsWithIt() {
        CombatState state = StsPvpTestStates.twoHumans(PLAYER_A.toString(), PLAYER_B.toString());
        Instant deadline = Instant.parse("2026-08-24T12:00:30Z");
        StsPvpBattle battle = battleHolding(state);
        battle.setTurnDeadline(deadline);
        theBattleIsReadBy(battle, PLAYER_A);

        StsPvpCombatView view =
                (StsPvpCombatView) controller.combatSnapshot(oidcUser, BATTLE_ID).payload();

        assertThat(view.turnDeadline())
                .as("a client that resynchronises still has a turn to count down")
                .isEqualTo(deadline);
        assertThat(view.serverTime()).isNotNull();
    }

    /** A player who is not in this battle gets a refusal, never somebody else's combat. */
    @Test
    void aStrangerToTheBattleIsRefusedRatherThanServed() {
        CombatState state = StsPvpTestStates.twoHumans(PLAYER_A.toString(), PLAYER_B.toString());
        theBattleIsReadBy(battleHolding(state), STRANGER);

        assertThatThrownBy(() -> controller.combatSnapshot(oidcUser, BATTLE_ID))
                .isInstanceOf(BadRequestException.class);
    }

    /** A battle whose combat has not opened yet has no snapshot to give, and says so. */
    @Test
    void aBattleWithoutACombatYetHasNothingToSnapshot() {
        StsPvpBattle pending = new StsPvpBattle();
        pending.setStatus(StsPvpBattleStatus.PENDING);
        pending.setTeams(teams());
        pending.setState(objectMapper.createObjectNode());
        pending.setHeartbeat(objectMapper.createObjectNode());
        theBattleIsReadBy(pending, PLAYER_A);

        assertThatThrownBy(() -> controller.combatSnapshot(oidcUser, BATTLE_ID))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void anUnknownBattleHasNoSnapshotEither() {
        when(synchronizationService.syncWithKeycloak(oidcUser)).thenReturn(user(PLAYER_A));
        when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.combatSnapshot(oidcUser, BATTLE_ID))
                .isInstanceOf(NotFoundException.class);
    }

    // ------------------------------------------------------------------------ fixtures

    private void theBattleIsReadBy(StsPvpBattle battle, UUID caller) {
        when(synchronizationService.syncWithKeycloak(oidcUser)).thenReturn(user(caller));
        when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.of(battle));
    }

    private StsPvpBattle battleHolding(CombatState state) {
        StsPvpBattle battle = new StsPvpBattle();
        battle.setStatus(StsPvpBattleStatus.ACTIVE);
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

    private static StsPvpCombatView.StsPvpCombatantView combatantOf(
            StsPvpCombatView view, UUID userId) {
        return view.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(userId.toString()))
                .findFirst()
                .orElseThrow();
    }

    private static String[] handInstanceIds(CombatState state, String combatantId) {
        return state.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow()
                .piles().hand().stream()
                .map(card -> card.instanceId())
                .toArray(String[]::new);
    }

    private static AppUser user(UUID id) {
        AppUser user = new AppUser();
        user.setId(id);
        return user;
    }
}
