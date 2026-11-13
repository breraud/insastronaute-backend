package fr.insastronaute.webapi.sts.multiplayer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import fr.insastronaute.webapi.sts.multiplayer.combat.StsPvpCombatService;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattle;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpChallenge;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpChallengeStatus;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpDeck;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpMode;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpNotification;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpNotificationType;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpProfile;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpChallengeDto;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpBattleRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpChallengeRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpDeckRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpNotificationRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpProfileRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpQueueEntryRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Un défi entre amis, de l'envoi à la bataille.
 *
 * <p>Ce qui se vérifie ici est ce qui manquait : un défi pouvait être envoyé, mais jamais accepté —
 * il n'ouvrait aucune bataille et expirait dans son coin. Et, avec l'acceptation, les gardes qui
 * vont avec : seul le défié accepte, un défi déjà tranché ne se tranche pas deux fois, et un défi
 * expiré ne rouvre rien.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StsPvpChallengeFlowTest {

    @Mock private StsPvpQueueEntryRepository queueRepository;
    @Mock private StsPvpProfileRepository profileRepository;
    @Mock private StsPvpChallengeRepository challengeRepository;
    @Mock private StsPvpBattleRepository battleRepository;
    @Mock private StsPvpNotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private FriendshipRepository friendshipRepository;
    @Mock private StsPvpDeckRepository deckRepository;
    @Mock private StsPvpCombatService combatService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private StsPvpService service;

    private final AppUser challenger = player("challenger-sub", "Alice");
    private final AppUser challenged = player("challenged-sub", "Bob");

    @Test
    void acceptingOpensADuelForBothPlayersAndTellsTheChallenger() {
        StsPvpChallenge challenge = pending(false);
        givenReady(challenger);
        givenReady(challenged);
        givenBattlesAreSaved();

        StsPvpChallengeDto accepted = service.acceptChallenge(challenged, challenge.getId());

        assertThat(accepted.status()).isEqualTo("ACCEPTED");
        assertThat(accepted.battleId()).isNotNull();
        assertThat(challenge.getBattleId()).isEqualTo(accepted.battleId());
        assertThat(challenge.getRespondedAt()).isNotNull();

        ArgumentCaptor<StsPvpBattle> battle = ArgumentCaptor.forClass(StsPvpBattle.class);
        verify(combatService).startCombat(battle.capture());
        assertThat(battle.getValue().getMode()).isEqualTo(StsPvpMode.ONE_V_ONE);
        assertThat(battle.getValue().getTeams().toString())
                .contains(challenger.getId().toString())
                .contains(challenged.getId().toString());

        // Le défiant attend peut-être hors du jeu : la notification porte la bataille à rejoindre.
        ArgumentCaptor<StsPvpNotification> notification = ArgumentCaptor.forClass(StsPvpNotification.class);
        verify(notificationRepository, org.mockito.Mockito.atLeastOnce()).save(notification.capture());
        assertThat(notification.getAllValues())
                .anySatisfy(sent -> {
                    assertThat(sent.getType()).isEqualTo(StsPvpNotificationType.CHALLENGE_ACCEPTED);
                    assertThat(sent.getRecipient()).isSameAs(challenger);
                    assertThat(sent.getPayload().path("battleId").asText()).isEqualTo(accepted.battleId().toString());
                });
    }

    @Test
    void aFriendlyChallengeOpensAFriendlyDuel() {
        StsPvpChallenge challenge = pending(true);
        givenReady(challenger);
        givenReady(challenged);
        givenBattlesAreSaved();

        service.acceptChallenge(challenged, challenge.getId());

        ArgumentCaptor<StsPvpBattle> battle = ArgumentCaptor.forClass(StsPvpBattle.class);
        verify(combatService).startCombat(battle.capture());
        assertThat(battle.getValue().getFriendly()).isTrue();
    }

    /**
     * Sans le verrou de l'appariement, l'un des deux pourrait être apparié par la file au même
     * instant et se retrouver dans deux batailles ; et les deux doivent quitter la file.
     */
    @Test
    void acceptingTakesTheMatchmakingLockFirstAndTakesBothPlayersOutOfTheQueue() {
        StsPvpChallenge challenge = pending(false);
        givenReady(challenger);
        givenReady(challenged);
        givenBattlesAreSaved();

        service.acceptChallenge(challenged, challenge.getId());

        var order = inOrder(queueRepository, challengeRepository);
        order.verify(queueRepository).lockMatchmaking();
        order.verify(challengeRepository).findByIdForUpdate(challenge.getId());
        verify(queueRepository).deleteByUser_KeycloakSub("challenger-sub");
        verify(queueRepository).deleteByUser_KeycloakSub("challenged-sub");
    }

    @Test
    void onlyTheChallengedPlayerCanAccept() {
        StsPvpChallenge challenge = pending(false);

        assertThatThrownBy(() -> service.acceptChallenge(challenger, challenge.getId()))
                .isInstanceOf(BadRequestException.class);
        verify(combatService, never()).startCombat(any());
    }

    @Test
    void aStrangerToTheChallengeCannotEvenSeeIt() {
        StsPvpChallenge challenge = pending(false);
        AppUser stranger = player("stranger-sub", "Eve");

        assertThatThrownBy(() -> service.acceptChallenge(stranger, challenge.getId()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.getChallenge(stranger, challenge.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    /** Accepter deux fois — un double clic, le site et le jeu — ne doit pas ouvrir deux batailles. */
    @Test
    void anAnsweredChallengeCannotBeAnsweredAgain() {
        StsPvpChallenge challenge = pending(false);
        challenge.setStatus(StsPvpChallengeStatus.ACCEPTED);

        assertThatThrownBy(() -> service.acceptChallenge(challenged, challenge.getId()))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.declineChallenge(challenged, challenge.getId()))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.cancelChallenge(challenger, challenge.getId()))
                .isInstanceOf(ConflictException.class);
        verify(combatService, never()).startCombat(any());
    }

    @Test
    void anExpiredChallengeOpensNothing() {
        StsPvpChallenge challenge = pending(false);
        challenge.setExpiresAt(Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> service.acceptChallenge(challenged, challenge.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("expiré");
        verify(combatService, never()).startCombat(any());
    }

    /**
     * Le défi part vers un ami qui, lui, a un deck — mais le défiant a pu effacer le sien depuis.
     * Le message doit nommer celui à qui il manque : « compose un deck » dit au défié serait faux.
     */
    @Test
    void aChallengerWhoLostTheirDeckIsNamedAsTheOneWhoCannotPlay() {
        StsPvpChallenge challenge = pending(false);
        givenReady(challenged);
        when(profileRepository.findByUserKeycloakSubForUpdate("challenger-sub"))
                .thenReturn(Optional.of(profile(challenger)));
        // pas de deck pour le défiant

        assertThatThrownBy(() -> service.acceptChallenge(challenged, challenge.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Alice");
        verify(combatService, never()).startCombat(any());
    }

    @Test
    void decliningClosesTheChallengeAndTellsTheChallenger() {
        StsPvpChallenge challenge = pending(false);

        StsPvpChallengeDto declined = service.declineChallenge(challenged, challenge.getId());

        assertThat(declined.status()).isEqualTo("DECLINED");
        ArgumentCaptor<StsPvpNotification> notification = ArgumentCaptor.forClass(StsPvpNotification.class);
        verify(notificationRepository).save(notification.capture());
        assertThat(notification.getValue().getType()).isEqualTo(StsPvpNotificationType.CHALLENGE_DECLINED);
        assertThat(notification.getValue().getRecipient()).isSameAs(challenger);
    }

    @Test
    void onlyTheChallengerCanCancel() {
        StsPvpChallenge challenge = pending(false);

        assertThatThrownBy(() -> service.cancelChallenge(challenged, challenge.getId()))
                .isInstanceOf(BadRequestException.class);

        assertThat(service.cancelChallenge(challenger, challenge.getId()).status()).isEqualTo("CANCELED");
    }

    @Test
    void eachPlayerSeesTheChallengeFromTheirOwnSide() {
        StsPvpChallenge challenge = pending(false);
        when(challengeRepository.findById(challenge.getId())).thenReturn(Optional.of(challenge));

        assertThat(service.getChallenge(challenged, challenge.getId()).direction()).isEqualTo("INCOMING");
        assertThat(service.getChallenge(challenger, challenge.getId()).direction()).isEqualTo("OUTGOING");
        assertThat(service.getChallenge(challenger, challenge.getId()).challengedName()).isEqualTo("Bob");
    }

    /** Une liste de défis en attente ne montre pas ceux dont le délai est passé. */
    @Test
    void theListLeavesExpiredChallengesOut() {
        StsPvpChallenge live = pending(false);
        StsPvpChallenge stale = pending(false);
        stale.setExpiresAt(Instant.now().minusSeconds(1));
        when(challengeRepository.findByChallenged_IdAndStatusOrderByCreatedAtDesc(
                challenged.getId(), StsPvpChallengeStatus.PENDING)).thenReturn(List.of(live, stale));
        when(challengeRepository.findByChallenger_IdAndStatusOrderByCreatedAtDesc(
                challenged.getId(), StsPvpChallengeStatus.PENDING)).thenReturn(List.of());

        List<StsPvpChallengeDto> challenges = service.listChallenges(challenged);

        assertThat(challenges).extracting(StsPvpChallengeDto::challengeId).containsExactly(live.getId());
        assertThat(stale.getStatus()).isEqualTo(StsPvpChallengeStatus.EXPIRED);
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private StsPvpChallenge pending(boolean friendly) {
        StsPvpChallenge challenge = new StsPvpChallenge();
        ReflectionTestUtils.setField(challenge, "id", UUID.randomUUID());
        challenge.setChallenger(challenger);
        challenge.setChallenged(challenged);
        challenge.setFriendly(friendly);
        challenge.setStatus(StsPvpChallengeStatus.PENDING);
        challenge.setExpiresAt(Instant.now().plusSeconds(600));
        when(challengeRepository.findByIdForUpdate(challenge.getId())).thenReturn(Optional.of(challenge));
        when(challengeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return challenge;
    }

    private void givenReady(AppUser player) {
        StsPvpProfile profile = profile(player);
        when(profileRepository.findByUserKeycloakSubForUpdate(player.getKeycloakSub())).thenReturn(Optional.of(profile));
        when(profileRepository.findByUser_KeycloakSub(player.getKeycloakSub())).thenReturn(Optional.of(profile));
        StsPvpDeck deck = new StsPvpDeck();
        deck.setSelectedCharacter("EP");
        deck.setCardIds(objectMapper.valueToTree(List.of("Frappe")));
        deck.setActive(true);
        when(deckRepository.findByOwner_KeycloakSubAndSelectedCharacterOrderByUpdatedAtDesc(player.getKeycloakSub(), "EP"))
                .thenReturn(List.of(deck));
    }

    private void givenBattlesAreSaved() {
        when(battleRepository.save(any())).thenAnswer(invocation -> {
            StsPvpBattle battle = invocation.getArgument(0);
            if (battle.getId() == null) {
                ReflectionTestUtils.setField(battle, "id", UUID.randomUUID());
            }
            return battle;
        });
    }

    private static StsPvpProfile profile(AppUser player) {
        StsPvpProfile profile = new StsPvpProfile();
        profile.setUser(player);
        profile.setElo(1000);
        profile.setSelectedCharacter("EP");
        return profile;
    }

    private static AppUser player(String subject, String displayName) {
        AppUser player = new AppUser();
        player.setId(UUID.randomUUID());
        player.setKeycloakSub(subject);
        player.setDisplayName(displayName);
        return player;
    }
}
