package fr.insastronaute.webapi.sts.multiplayer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.friend.domain.Friendship;
import fr.insastronaute.webapi.friend.domain.FriendshipStatus;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpChallenge;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattle;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpBattleStatus;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpQueueEntry;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpProfile;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpNotification;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpMode;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpNotificationType;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpChallengeRequest;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpQuickMatchRequest;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpChallengeRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpBattleRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpNotificationRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpQueueEntryRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpProfileRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.List;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class StsPvpMatchmakingServiceTest {

    @Mock private StsPvpQueueEntryRepository queueRepository;
    @Mock private StsPvpProfileRepository profileRepository;
    @Mock private StsPvpChallengeRepository challengeRepository;
    @Mock private StsPvpBattleRepository battleRepository;
    @Mock private StsPvpNotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private FriendshipRepository friendshipRepository;
    @Mock private fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpDeckRepository deckRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private StsPvpService service;

    @Test
    void cancellingQuickMatchRemovesOnlyTheAuthenticatedPlayerFromTheQueue() {
        AppUser player = player("player-sub");

        service.cancelQuickMatch(player);

        verify(queueRepository).deleteByUser_KeycloakSub("player-sub");
    }

    @Test
    void cancellingQuickMatchClaimsTheDatabaseMutexBeforeDeletingTheQueueEntry() {
        AppUser player = player("player-sub");

        service.cancelQuickMatch(player);

        var order = inOrder(queueRepository);
        order.verify(queueRepository).lockMatchmaking();
        order.verify(queueRepository).deleteByUser_KeycloakSub("player-sub");
    }

    @Test
    void heartbeatRefreshesOnlyAnExistingEntryUnderTheDatabaseMutex() {
        AppUser player = player("player-sub");
        StsPvpQueueEntry entry = new StsPvpQueueEntry();
        entry.setUser(player);
        when(queueRepository.findByUser_KeycloakSub("player-sub")).thenReturn(Optional.of(entry));

        var response = service.heartbeatQuickMatch(player);

        var order = inOrder(queueRepository);
        order.verify(queueRepository).lockMatchmaking();
        order.verify(queueRepository).deleteByUpdatedAtBefore(any());
        order.verify(queueRepository).findByUser_KeycloakSub("player-sub");
        order.verify(queueRepository).save(entry);
        assertThat(response.queued()).isTrue();
    }

    @Test
    void cancellationReturnsTheBattleThatWonTheQueueRace() {
        AppUser player = player("player-sub");
        UUID battleId = UUID.randomUUID();
        StsPvpNotification notification = new StsPvpNotification();
        notification.setPayload(objectMapper.createObjectNode().put("battleId", battleId.toString()));
        ReflectionTestUtils.setField(notification, "createdAt", Instant.now());
        StsPvpBattle battle = new StsPvpBattle();
        ReflectionTestUtils.setField(battle, "id", battleId);
        battle.setStatus(StsPvpBattleStatus.PENDING);
        when(queueRepository.deleteByUser_KeycloakSub("player-sub")).thenReturn(0L);
        when(notificationRepository.findFirstByRecipientAndTypeAndReadAtIsNullOrderByCreatedAtDesc(
                player, StsPvpNotificationType.QUICK_MATCH_FOUND)).thenReturn(Optional.of(notification));
        when(battleRepository.findById(battleId)).thenReturn(Optional.of(battle));

        var response = service.cancelQuickMatch(player);

        assertThat(response.cancelled()).isFalse();
        assertThat(response.battleId()).isEqualTo(battleId);
    }

    @Test
    void cancellationIgnoresAnOldUnreadMatchNotification() {
        AppUser player = player("player-sub");
        StsPvpNotification notification = new StsPvpNotification();
        notification.setPayload(objectMapper.createObjectNode().put("battleId", UUID.randomUUID().toString()));
        ReflectionTestUtils.setField(notification, "createdAt", Instant.now().minusSeconds(121));
        when(queueRepository.deleteByUser_KeycloakSub("player-sub")).thenReturn(0L);
        when(notificationRepository.findFirstByRecipientAndTypeAndReadAtIsNullOrderByCreatedAtDesc(
                player, StsPvpNotificationType.QUICK_MATCH_FOUND)).thenReturn(Optional.of(notification));

        var response = service.cancelQuickMatch(player);

        assertThat(response.cancelled()).isTrue();
        assertThat(response.battleId()).isNull();
    }

    @Test
    void quickMatchClaimsTheDatabaseMutexBeforeReadingAnyPlayerQueueState() {
        AppUser player = player("player-sub");
        StsPvpProfile profile = readyProfile(player, "MECA");
        when(profileRepository.findByUserKeycloakSubForUpdate("player-sub")).thenReturn(Optional.of(profile));
        givenDeck("player-sub", "MECA");
        when(queueRepository.findByUser_KeycloakSub("player-sub")).thenReturn(Optional.empty());
        when(queueRepository.findByModeAndFriendlyOrderByQueuedAtAsc(StsPvpMode.ONE_V_ONE, false))
                .thenReturn(List.of());
        when(queueRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.quickMatch(player, new StsPvpQuickMatchRequest(false, false, null, null));

        var order = inOrder(queueRepository, profileRepository);
        order.verify(queueRepository).lockMatchmaking();
        order.verify(queueRepository).deleteByUpdatedAtBefore(any());
        order.verify(profileRepository).findByUserKeycloakSubForUpdate("player-sub");
    }

    @Test
    void aQuickChallengeRejectsAPlayerWhoIsNotAnAcceptedFriend() {
        AppUser challenger = player("challenger-sub");
        AppUser stranger = player("stranger-sub");
        when(userRepository.findById(stranger.getId())).thenReturn(Optional.of(stranger));
        when(friendshipRepository.findBetween(challenger.getId(), stranger.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendChallenge(
                challenger,
                new StsPvpChallengeRequest(stranger.getId().toString(), true)
        )).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ami");

        verify(challengeRepository, never()).save(any());
    }

    @Test
    void aQuickChallengeAcceptsAnAcceptedFriend() {
        AppUser challenger = player("challenger-sub");
        AppUser friend = player("friend-sub");
        Friendship friendship = friendship(challenger, friend, FriendshipStatus.ACCEPTED);
        when(userRepository.findById(friend.getId())).thenReturn(Optional.of(friend));
        when(friendshipRepository.findBetween(challenger.getId(), friend.getId())).thenReturn(Optional.of(friendship));
        // Défier engage l'autre : le défiant doit pouvoir se battre avant qu'on le laisse faire.
        when(profileRepository.findByUserKeycloakSubForUpdate("challenger-sub"))
                .thenReturn(Optional.of(readyProfile(challenger, "MECA")));
        givenDeck("challenger-sub", "MECA");
        when(challengeRepository.save(any())).thenAnswer(invocation -> {
            StsPvpChallenge challenge = invocation.getArgument(0);
            ReflectionTestUtils.setField(challenge, "id", UUID.randomUUID());
            return challenge;
        });

        service.sendChallenge(challenger, new StsPvpChallengeRequest(friend.getId().toString(), true));

        verify(challengeRepository).save(any());
        verify(notificationRepository).save(any());
    }

    @Test
    void aPendingFriendRequestDoesNotAuthorizeAChallenge() {
        AppUser challenger = player("challenger-sub");
        AppUser pending = player("pending-sub");
        when(userRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(friendshipRepository.findBetween(challenger.getId(), pending.getId()))
                .thenReturn(Optional.of(friendship(challenger, pending, FriendshipStatus.PENDING)));

        assertThatThrownBy(() -> service.sendChallenge(
                challenger,
                new StsPvpChallengeRequest(pending.getId().toString(), false)
        )).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ami");
    }

    private static Friendship friendship(AppUser requester, AppUser addressee, FriendshipStatus status) {
        Friendship friendship = new Friendship();
        friendship.setRequester(requester);
        friendship.setAddressee(addressee);
        friendship.setStatus(status);
        return friendship;
    }


    /**
     * Le manque de deck ne se voyait qu'à la création du combat, une fois les deux
     * joueurs appariés : celui qui était prêt se prenait l'échec de l'autre. Le refus
     * appartient à l'entrée en file, où il ne concerne que celui qui le provoque.
     */
    @Test
    void searchingWithoutADeckIsRefusedBeforeAnyOpponentIsInvolved() {
        AppUser player = player("player-sub");
        when(profileRepository.findByUserKeycloakSubForUpdate("player-sub"))
                .thenReturn(Optional.of(readyProfile(player, "MECA")));
        when(deckRepository.findByOwner_KeycloakSubAndSelectedCharacterOrderByUpdatedAtDesc("player-sub", "MECA"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.quickMatch(player, new StsPvpQuickMatchRequest(false, false, null, null)))
                .hasMessage("Compose un deck pour MECA avant de chercher un combat.");

        verify(queueRepository, never()).save(any());
        verify(queueRepository, never()).findByFriendlyOrderByQueuedAtAsc(anyBoolean());
    }

    /**
     * Un profil neuf n'a pas de personnage, et le combat lui en attribuait un par
     * défaut — d'où un message réclamant un deck pour un personnage que le joueur
     * n'avait jamais choisi.
     */
    @Test
    void searchingWithoutHavingChosenACharacterSaysSo() {
        AppUser player = player("player-sub");
        when(profileRepository.findByUserKeycloakSubForUpdate("player-sub"))
                .thenReturn(Optional.of(readyProfile(player, null)));

        assertThatThrownBy(() -> service.quickMatch(player, new StsPvpQuickMatchRequest(false, false, null, null)))
                .hasMessage("Choisis ton personnage dans le menu multijoueur avant de chercher un combat.");

        verify(queueRepository, never()).save(any());
    }

    @Test
    void anEmptyDeckDoesNotCountAsADeck() {
        AppUser player = player("player-sub");
        when(profileRepository.findByUserKeycloakSubForUpdate("player-sub"))
                .thenReturn(Optional.of(readyProfile(player, "MECA")));
        fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpDeck empty =
                new fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpDeck();
        empty.setSelectedCharacter("MECA");
        empty.setCardIds(objectMapper.createArrayNode());
        when(deckRepository.findByOwner_KeycloakSubAndSelectedCharacterOrderByUpdatedAtDesc("player-sub", "MECA"))
                .thenReturn(List.of(empty));

        assertThatThrownBy(() -> service.quickMatch(player, new StsPvpQuickMatchRequest(false, false, null, null)))
                .hasMessage("Compose un deck pour MECA avant de chercher un combat.");
    }

    private StsPvpProfile readyProfile(AppUser player, String character) {
        StsPvpProfile profile = new StsPvpProfile();
        profile.setUser(player);
        profile.setElo(1000);
        profile.setSelectedCharacter(character);
        return profile;
    }

    private void givenDeck(String subject, String character) {
        fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpDeck deck =
                new fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpDeck();
        deck.setSelectedCharacter(character);
        deck.setCardIds(objectMapper.valueToTree(List.of("katana")));
        deck.setActive(true);
        when(deckRepository.findByOwner_KeycloakSubAndSelectedCharacterOrderByUpdatedAtDesc(subject, character))
                .thenReturn(List.of(deck));
    }

    private static AppUser player(String subject) {
        AppUser player = new AppUser();
        player.setId(UUID.randomUUID());
        player.setKeycloakSub(subject);
        return player;
    }
}
