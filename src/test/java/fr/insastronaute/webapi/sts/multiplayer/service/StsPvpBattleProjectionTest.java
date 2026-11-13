package fr.insastronaute.webapi.sts.multiplayer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.card.service.CardService;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatBroadcaster;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatService;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpTestStates;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattle;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattleStatus;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpBattleDto;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpBattleRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpChallengeRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpDeckRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpNotificationRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpProfileRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpQueueEntryRepository;
import fr.insastronaute.webapi.sts.repository.StsRunRepository;
import fr.insastronaute.webapi.sts.service.StsGameDataService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What a player is handed when it reads its battle over HTTP.
 *
 * <p>{@code GET /api/sts/pvp/battles/{id}} and the action endpoint both answer with a battle DTO,
 * to one player at a time. The battle's stored state is the authoritative {@code CombatState},
 * which carries the opponent's hand and the seed the whole future draw order comes from, so the
 * DTO must carry the projection rather than the state — the same leak the socket closes, closed
 * on the HTTP path too.
 */
class StsPvpBattleProjectionTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final StsPvpBattleRepository battleRepository = mock(StsPvpBattleRepository.class);
    private final StsPvpDeckRepository deckRepository = mock(StsPvpDeckRepository.class);
    private final StsPvpProfileRepository profileRepository = mock(StsPvpProfileRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final StsGameDataService gameDataService = mock(StsGameDataService.class);

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

    private static final UUID BATTLE_ID = UUID.fromString("aaaaaaaa-0000-4000-8000-00000000000b");
    private static final UUID PLAYER_A = UUID.fromString("11111111-0000-4000-8000-000000000001");
    private static final UUID PLAYER_B = UUID.fromString("22222222-0000-4000-8000-000000000002");

    @Test
    void theBattleReadByAPlayerCarriesNeitherTheOpponentCardsNorTheRandomSeed() throws Exception {
        CombatState state = StsPvpTestStates.twoHumans(PLAYER_A.toString(), PLAYER_B.toString());
        StsPvpBattle battle = battleHolding(state);
        when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.of(battle));

        StsPvpBattleDto dto = service.getBattleState(user(PLAYER_A), BATTLE_ID);

        String json = objectMapper.writeValueAsString(dto);
        assertThat(json)
                .as("player A must still be dealt its own hand")
                .contains(handInstanceIds(state, PLAYER_A.toString()));
        assertThat(json)
                .as("player B's cards are player B's alone")
                .doesNotContain(handInstanceIds(state, PLAYER_B.toString()));
        assertThat(json)
                .as("the seed replays every future draw of both players")
                .doesNotContain("random")
                .doesNotContain(String.valueOf(state.random().value()));
    }

    @Test
    void aBattleWithoutACombatYetAnswersWithoutAState() {
        StsPvpBattle battle = new StsPvpBattle();
        battle.setStatus(StsPvpBattleStatus.PENDING);
        battle.setTeams(teams());
        battle.setState(objectMapper.createObjectNode());
        battle.setHeartbeat(objectMapper.createObjectNode());
        when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.of(battle));

        StsPvpBattleDto dto = service.getBattleState(user(PLAYER_A), BATTLE_ID);

        assertThat(dto.state()).isNull();
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
