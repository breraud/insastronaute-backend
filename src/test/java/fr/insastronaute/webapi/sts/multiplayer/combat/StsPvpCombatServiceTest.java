package fr.insastronaute.webapi.sts.multiplayer.combat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.mock;

import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.ForbiddenException;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattle;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattleStatus;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpDeck;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpProfile;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpDeckRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpProfileRepository;
import fr.insastronaute.webapi.sts.service.StsGameDataService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The PvP battle driven by the authoritative engine.
 *
 * <p>These run the real {@code CombatFactory} and {@code CombatEngine} against mocked
 * persistence: what is under test is the wiring — which deck becomes which combatant, who is
 * allowed to speak, and what the battle records once the engine says the combat is over — and
 * a mocked engine would only re-assert the wiring's own assumptions.
 */
@ExtendWith(MockitoExtension.class)
class StsPvpCombatServiceTest {

    private static final UUID BATTLE_ID = UUID.fromString("0e5f9a2c-0000-4000-8000-0000000000ff");
    private static final UUID FIRST_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID SECOND_USER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String FIRST_SUB = "sub-first";
    private static final String SECOND_SUB = "sub-second";
    private static final String CHARACTER = "EP";
    private static final int CHARACTER_HP = 72;
    private static final String DATA_VERSION = "2026.8.0";
    private static final Instant NOW = Instant.parse("2026-08-22T10:15:30Z");

    /** A card that kills a 80 hp PvP combatant outright, so one command can finish a combat. */
    private static final String FINISHER = "finisher";
    private static final String STRIKE = "strike";
    private static final String CATALOG = """
            {"dataVersion":"2026.8.0","cards":[
              {"id":"strike","cardName":"Frappe","cost":1,"targetingMode":"Enemy",
               "effects":[{"type":"Damage","value":6}]},
              {"id":"finisher","cardName":"Achèvement","cost":1,"targetingMode":"Enemy",
               "effects":[{"type":"Damage","value":200}]},
              {"id":"unused","cardName":"Inutilisée","cost":1,"targetingMode":"Enemy",
               "effects":[{"type":"Damage","value":1}]}
            ]}""";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock private StsGameDataService gameDataService;
    @Mock private StsPvpDeckRepository deckRepository;
    @Mock private StsPvpProfileRepository profileRepository;
    @Mock private UserRepository userRepository;

    private StsPvpCombatService service;

    @BeforeEach
    void setUp() {
        service = new StsPvpCombatService(
            objectMapper, gameDataService, deckRepository, profileRepository, userRepository,
            mock(AutoChallengeTriggerPublisher.class), List.of(),
            new fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentEngine(List.of()));
        TimeTravelContext.setForcedTime(NOW);
    }

    @AfterEach
    void tearDown() {
        TimeTravelContext.clear();
    }

    @Test
    void startingACombatStoresAnAuthoritativeStateAndActivatesTheBattle() {
        StsPvpBattle battle = battle();
        gameDataIsReadable();
        bothPlayersHaveAnActiveDeck(FINISHER, STRIKE);

        CombatState state = service.startCombat(battle);

        assertThat(battle.getStatus()).isEqualTo(StsPvpBattleStatus.ACTIVE);
        assertThat(state.combatants()).hasSize(2);
        assertThat(state.combatants()).allSatisfy(combatant -> {
            assertThat(combatant.hp()).isEqualTo(CHARACTER_HP);
            assertThat(combatant.maxHp()).isEqualTo(CHARACTER_HP);
        });
        assertThat(state.combatants().stream().map(CombatantState::combatantId))
                .containsExactly(FIRST_USER_ID.toString(), SECOND_USER_ID.toString());
        assertThat(state.combatants().stream().map(CombatantState::teamId))
                .containsExactly("team-0", "team-1");

        CombatState stored = storedState(battle);
        assertThat(stored.combatId()).isEqualTo(BATTLE_ID);
        assertThat(stored.revision()).isEqualTo(state.revision());
        assertThat(stored.combatants()).hasSize(2);
        // The deck each player brought is the deck their combatant draws from.
        assertThat(cardIdsOf(stored, FIRST_USER_ID)).containsOnly(FINISHER);
        assertThat(cardIdsOf(stored, SECOND_USER_ID)).containsOnly(STRIKE);
    }

    @Test
    void aCommandFromTheWaitingPlayerIsRefused() {
        StsPvpBattle battle = battle();
        gameDataIsReadable();
        bothPlayersHaveAnActiveDeck(FINISHER, STRIKE);
        CombatState started = service.startCombat(battle);
        // The engine's timeline gives the first team the opening turn.
        assertThat(started.activeCombatantId()).isEqualTo(FIRST_USER_ID.toString());
        JsonNode stateBefore = battle.getState().deepCopy();

        assertThatThrownBy(() -> service.applyCommand(
                battle,
                SECOND_USER_ID,
                new EndTurnCommand(BATTLE_ID, started.revision(), SECOND_USER_ID.toString())))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("tour");

        assertThat(battle.getState()).isEqualTo(stateBefore);
        assertThat(battle.getStatus()).isEqualTo(StsPvpBattleStatus.ACTIVE);
    }

    @Test
    void anAcceptedCommandAdvancesTheStoredState() {
        StsPvpBattle battle = battle();
        gameDataIsReadable();
        bothPlayersHaveAnActiveDeck(FINISHER, STRIKE);
        CombatState started = service.startCombat(battle);

        StsPvpCombatService.StsPvpCommandOutcome outcome = service.applyCommand(
                battle,
                FIRST_USER_ID,
                new EndTurnCommand(BATTLE_ID, started.revision(), FIRST_USER_ID.toString()));

        assertThat(outcome.finished()).isFalse();
        assertThat(outcome.events()).isNotEmpty();
        assertThat(outcome.state().revision()).isGreaterThan(started.revision());
        assertThat(storedState(battle).revision()).isEqualTo(outcome.state().revision());
        // Ending a turn hands the timeline to the other player, and nobody plays for them.
        assertThat(outcome.state().activeCombatantId()).isEqualTo(SECOND_USER_ID.toString());
        assertThat(battle.getStatus()).isEqualTo(StsPvpBattleStatus.ACTIVE);
        assertThat(battle.getWinnerTeam()).isNull();
        assertThat(battle.getTurnDeadline()).isNotNull();
    }

    @Test
    void aWipedOutTeamFinishesTheBattleAndMovesTheRating() {
        StsPvpBattle battle = battle();
        gameDataIsReadable();
        bothPlayersHaveAnActiveDeck(FINISHER, STRIKE);
        CombatState started = service.startCombat(battle);
        StsPvpProfile winner = profile(1000);
        StsPvpProfile loser = profile(1000);
        bothPlayersHaveAProfile(winner, loser);

        StsPvpCombatService.StsPvpCommandOutcome outcome = service.applyCommand(
                battle,
                FIRST_USER_ID,
                new PlayCardCommand(
                        BATTLE_ID,
                        started.revision(),
                        FIRST_USER_ID.toString(),
                        firstCardInHand(started, FIRST_USER_ID),
                        List.of(SECOND_USER_ID.toString())));

        assertThat(outcome.finished()).isTrue();
        assertThat(outcome.state().status()).isEqualTo(CombatStatus.FINISHED);
        assertThat(outcome.state().winnerTeamId()).isEqualTo("team-0");
        assertThat(battle.getStatus()).isEqualTo(StsPvpBattleStatus.FINISHED);
        assertThat(battle.getWinnerTeam()).isZero();

        assertThat(winner.getElo()).isEqualTo(1016);
        assertThat(loser.getElo()).isEqualTo(984);
        assertThat(winner.getWinsRanked()).isEqualTo(1);
        assertThat(loser.getLossesRanked()).isEqualTo(1);
        verify(profileRepository).save(winner);
        verify(profileRepository).save(loser);
    }

    /**
     * The heartbeat is stamped by the engine path, not by the HTTP endpoint.
     *
     * <p>A player who only ever speaks over the socket used to look silent to the battle: the
     * stamp lived in {@code StsPvpService.submitBattleAction} alone, so playing the whole combat
     * over the socket left the heartbeat frozen at the battle's creation and the disconnect sweep
     * would forfeit a player in the middle of the match they were winning. Stamping it where the
     * command is accepted is what makes both entry points agree.
     */
    @Test
    void anAcceptedCommandStampsTheActingPlayersHeartbeat() {
        StsPvpBattle battle = battle();
        gameDataIsReadable();
        bothPlayersHaveAnActiveDeck(FINISHER, STRIKE);
        CombatState started = service.startCombat(battle);
        long opponentSeenAt = NOW.minusSeconds(90L).toEpochMilli();
        battle.setHeartbeat(objectMapper.createObjectNode()
                .put(FIRST_USER_ID.toString(), NOW.minusSeconds(90L).toEpochMilli())
                .put(SECOND_USER_ID.toString(), opponentSeenAt));

        service.applyCommand(
                battle,
                FIRST_USER_ID,
                new EndTurnCommand(BATTLE_ID, started.revision(), FIRST_USER_ID.toString()));

        assertThat(battle.getHeartbeat().path(FIRST_USER_ID.toString()).asLong())
                .isEqualTo(NOW.toEpochMilli());
        // Only the player who spoke is heard from: the other one's silence is still silence.
        assertThat(battle.getHeartbeat().path(SECOND_USER_ID.toString()).asLong())
                .isEqualTo(opponentSeenAt);
    }

    @Test
    void aHeartbeatIsRecordedWithoutACommand() {
        StsPvpBattle battle = battle();

        service.recordHeartbeat(battle, FIRST_USER_ID);

        assertThat(battle.getHeartbeat().path(FIRST_USER_ID.toString()).asLong())
                .isEqualTo(NOW.toEpochMilli());
    }

    /**
     * A battle a sweep has already settled takes no more commands.
     *
     * <p>Without this, a player forfeited for having gone silent could come back a second later,
     * play on the state the forfeit left untouched, and have the rating moved a second time.
     */
    @Test
    void aCommandOnABattleThatIsNoLongerActiveIsRefused() {
        StsPvpBattle battle = battle();
        gameDataIsReadable();
        bothPlayersHaveAnActiveDeck(FINISHER, STRIKE);
        CombatState started = service.startCombat(battle);
        battle.setStatus(StsPvpBattleStatus.FINISHED);
        JsonNode stateBefore = battle.getState().deepCopy();

        assertThatThrownBy(() -> service.applyCommand(
                battle,
                FIRST_USER_ID,
                new EndTurnCommand(BATTLE_ID, started.revision(), FIRST_USER_ID.toString())))
                .isInstanceOf(ConflictException.class);

        assertThat(battle.getState()).isEqualTo(stateBefore);
    }

    @Test
    void startingACombatAppliesTheConfiguredPvpHpMultiplier() throws Exception {
        StsPvpBattle battle = battle();
        gameDataIsReadable(objectMapper.readTree("{\"combat\":{\"playerHpMultiplier\":1.5}}"));
        bothPlayersHaveAnActiveDeck(FINISHER, STRIKE);

        CombatState state = service.startCombat(battle);

        assertThat(state.combatants()).allSatisfy(combatant -> {
            assertThat(combatant.hp()).isEqualTo(108);
            assertThat(combatant.maxHp()).isEqualTo(108);
        });
    }

    private void gameDataIsReadable() {
        gameDataIsReadable(objectMapper.createObjectNode());
    }

    private void gameDataIsReadable(JsonNode pvpRating) {
        when(gameDataService.latestVersion()).thenReturn(DATA_VERSION);
        when(gameDataService.cards(DATA_VERSION)).thenReturn(catalog());
        when(gameDataService.character(DATA_VERSION, CHARACTER)).thenReturn(objectMapper.createObjectNode()
                .put("characterId", CHARACTER)
                .put("hp", CHARACTER_HP));
        when(gameDataService.pvpRating(DATA_VERSION)).thenReturn(pvpRating);
    }

    private void bothPlayersHaveAnActiveDeck(String firstCardId, String secondCardId) {
        when(userRepository.findById(FIRST_USER_ID)).thenReturn(Optional.of(user(FIRST_USER_ID, FIRST_SUB)));
        when(userRepository.findById(SECOND_USER_ID)).thenReturn(Optional.of(user(SECOND_USER_ID, SECOND_SUB)));
        when(deckRepository.findByOwner_KeycloakSubAndSelectedCharacterOrderByUpdatedAtDesc(FIRST_SUB, CHARACTER))
                .thenReturn(List.of(deck(firstCardId, false), deck(firstCardId, true)));
        when(deckRepository.findByOwner_KeycloakSubAndSelectedCharacterOrderByUpdatedAtDesc(SECOND_SUB, CHARACTER))
                .thenReturn(List.of(deck(secondCardId, true)));
    }

    private void bothPlayersHaveAProfile(StsPvpProfile first, StsPvpProfile second) {
        when(profileRepository.findByUser_KeycloakSub(FIRST_SUB)).thenReturn(Optional.of(first));
        when(profileRepository.findByUser_KeycloakSub(SECOND_SUB)).thenReturn(Optional.of(second));
    }

    private JsonNode catalog() {
        try {
            return objectMapper.readTree(CATALOG);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private StsPvpBattle battle() {
        StsPvpBattle battle = new StsPvpBattle();
        ReflectionTestUtils.setField(battle, "id", BATTLE_ID);
        battle.setFriendly(false);
        battle.setStatus(StsPvpBattleStatus.PENDING);
        battle.setTeams(objectMapper.createArrayNode()
                .add(objectMapper.createArrayNode().add(participant(FIRST_USER_ID)))
                .add(objectMapper.createArrayNode().add(participant(SECOND_USER_ID))));
        battle.setState(objectMapper.createObjectNode());
        battle.setHeartbeat(objectMapper.createObjectNode());
        return battle;
    }

    private JsonNode participant(UUID userId) {
        return objectMapper.createObjectNode()
                .put("userId", userId.toString())
                .put("displayName", "Joueur")
                .put("department", "")
                .put("selectedCharacter", CHARACTER);
    }

    private AppUser user(UUID id, String keycloakSub) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setKeycloakSub(keycloakSub);
        return user;
    }

    private StsPvpDeck deck(String cardId, boolean active) {
        StsPvpDeck deck = new StsPvpDeck();
        deck.setSelectedCharacter(CHARACTER);
        deck.setActive(active);
        deck.setCardIds(objectMapper.valueToTree(
                List.of(cardId, cardId, cardId, cardId, cardId, cardId, cardId, cardId, cardId, cardId)));
        return deck;
    }

    private StsPvpProfile profile(int elo) {
        StsPvpProfile profile = new StsPvpProfile();
        profile.setElo(elo);
        profile.setWinsRanked(0);
        profile.setLossesRanked(0);
        profile.setWinsFriendly(0);
        profile.setLossesFriendly(0);
        return profile;
    }

    private CombatState storedState(StsPvpBattle battle) {
        try {
            return objectMapper.treeToValue(battle.getState(), CombatState.class);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private CombatantState combatant(CombatState state, UUID userId) {
        return state.combatants().stream()
                .filter(candidate -> candidate.combatantId().equals(userId.toString()))
                .findFirst()
                .orElseThrow();
    }

    private List<String> cardIdsOf(CombatState state, UUID userId) {
        CombatantState combatant = combatant(state, userId);
        return java.util.stream.Stream.concat(
                        combatant.piles().draw().stream(),
                        combatant.piles().hand().stream())
                .map(card -> card.definitionId())
                .toList();
    }

    private String firstCardInHand(CombatState state, UUID userId) {
        return combatant(state, userId).piles().hand().getFirst().instanceId();
    }
}
