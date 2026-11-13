package fr.insastronaute.webapi.friend.service;

import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.challenge.repository.ChallengeRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.friend.domain.Friendship;
import fr.insastronaute.webapi.friend.domain.FriendshipStatus;
import fr.insastronaute.webapi.friend.dto.FriendProfileDto;
import fr.insastronaute.webapi.friend.dto.FriendRequestDto;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardConfig;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardEntry;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardMetric;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardScope;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardSnapshot;
import fr.insastronaute.webapi.leaderboard.repository.LeaderboardConfigRepository;
import fr.insastronaute.webapi.leaderboard.repository.LeaderboardEntryRepository;
import fr.insastronaute.webapi.leaderboard.repository.LeaderboardSnapshotRepository;
import fr.insastronaute.webapi.notification.domain.NotificationType;
import fr.insastronaute.webapi.notification.service.NotificationService;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FriendServiceTest {

    private FriendshipRepository friendshipRepository;
    private UserRepository userRepository;
    private NotificationService notificationService;
    private DepartmentRepository departmentRepository;
    private CardRepository cardRepository;
    private CardCollectionRepository cardCollectionRepository;
    private ChallengeRepository challengeRepository;
    private ChallengeSubmissionRepository submissionRepository;
    private LeaderboardConfigRepository leaderboardConfigRepository;
    private LeaderboardSnapshotRepository leaderboardSnapshotRepository;
    private LeaderboardEntryRepository leaderboardEntryRepository;
    private FriendService friendService;

    @BeforeEach
    void setUp() {
        friendshipRepository = mock(FriendshipRepository.class);
        userRepository = mock(UserRepository.class);
        notificationService = mock(NotificationService.class);
        departmentRepository = mock(DepartmentRepository.class);
        cardRepository = mock(CardRepository.class);
        cardCollectionRepository = mock(CardCollectionRepository.class);
        challengeRepository = mock(ChallengeRepository.class);
        submissionRepository = mock(ChallengeSubmissionRepository.class);
        leaderboardConfigRepository = mock(LeaderboardConfigRepository.class);
        leaderboardSnapshotRepository = mock(LeaderboardSnapshotRepository.class);
        leaderboardEntryRepository = mock(LeaderboardEntryRepository.class);
        friendService = new FriendService(
                friendshipRepository,
                userRepository,
                notificationService,
                departmentRepository,
                cardRepository,
                cardCollectionRepository,
                challengeRepository,
                submissionRepository,
                leaderboardConfigRepository,
                leaderboardSnapshotRepository,
                leaderboardEntryRepository, mock(AutoChallengeTriggerPublisher.class));
    }

    @Test
    void sendRequestCreatesPendingFriendshipAndNotifiesTarget() {
        AppUser requester = user(UUID.randomUUID(), "Alice");
        AppUser addressee = user(UUID.randomUUID(), "Bob");
        UUID requestId = UUID.randomUUID();

        when(userRepository.findById(addressee.getId())).thenReturn(Optional.of(addressee));
        when(friendshipRepository.findBetween(requester.getId(), addressee.getId())).thenReturn(Optional.empty());
        when(friendshipRepository.save(any())).thenAnswer(invocation -> {
            Friendship friendship = invocation.getArgument(0);
            friendship.setId(requestId);
            return friendship;
        });

        FriendRequestDto result = friendService.sendRequest(requester, addressee.getId());

        ArgumentCaptor<Friendship> friendshipCaptor = ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipRepository).save(friendshipCaptor.capture());
        Friendship savedFriendship = friendshipCaptor.getValue();

        assertThat(savedFriendship.getRequester()).isEqualTo(requester);
        assertThat(savedFriendship.getAddressee()).isEqualTo(addressee);
        assertThat(savedFriendship.getStatus()).isEqualTo(FriendshipStatus.PENDING);
        assertThat(result.id()).isEqualTo(requestId);

        verify(notificationService).notifyUser(
                eq(addressee.getId()),
                eq(requester.getId()),
                eq(NotificationType.FRIEND_REQUEST_RECEIVED),
                eq("Nouvelle demande d'ami"),
                eq("Alice veut t'ajouter en ami."),
                eq(Map.of(
                        "friendRequestId", requestId.toString(),
                        "requesterUserId", requester.getId().toString()
                )),
                eq("FRIEND_REQUEST"),
                eq(requestId)
        );
    }

    @Test
    void sendRequestRejectsSelfRequest() {
        AppUser requester = user(UUID.randomUUID(), "Alice");

        assertThatThrownBy(() -> friendService.sendRequest(requester, requester.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Vous ne pouvez pas vous ajouter vous-meme en ami.");

        verify(friendshipRepository, never()).save(any());
    }

    @Test
    void sendRequestRejectsExistingAcceptedFriendship() {
        AppUser requester = user(UUID.randomUUID(), "Alice");
        AppUser addressee = user(UUID.randomUUID(), "Bob");
        Friendship existingFriendship = friendship(requester, addressee, FriendshipStatus.ACCEPTED);

        when(userRepository.findById(addressee.getId())).thenReturn(Optional.of(addressee));
        when(friendshipRepository.findBetween(requester.getId(), addressee.getId())).thenReturn(Optional.of(existingFriendship));

        assertThatThrownBy(() -> friendService.sendRequest(requester, addressee.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Vous etes deja amis.");

        verify(friendshipRepository, never()).save(any());
    }

    @Test
    void acceptRequestRequiresCurrentUserToBeAddressee() {
        AppUser requester = user(UUID.randomUUID(), "Alice");
        AppUser addressee = user(UUID.randomUUID(), "Bob");
        Friendship friendship = friendship(requester, addressee, FriendshipStatus.PENDING);
        friendship.setId(UUID.randomUUID());

        when(friendshipRepository.findDetailedById(friendship.getId())).thenReturn(Optional.of(friendship));

        assertThatThrownBy(() -> friendService.acceptRequest(requester, friendship.getId()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Demande d'ami introuvable.");

        verify(notificationService, never()).notifyUser(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void acceptRequestMarksFriendshipAcceptedAndNotifiesRequester() {
        AppUser requester = user(UUID.randomUUID(), "Alice");
        AppUser addressee = user(UUID.randomUUID(), "Bob");
        Friendship friendship = friendship(requester, addressee, FriendshipStatus.PENDING);
        friendship.setId(UUID.randomUUID());

        when(friendshipRepository.findDetailedById(friendship.getId())).thenReturn(Optional.of(friendship));
        when(friendshipRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FriendRequestDto result = friendService.acceptRequest(addressee, friendship.getId());

        assertThat(result.status()).isEqualTo(FriendshipStatus.ACCEPTED);
        assertThat(result.respondedAt()).isNotNull();
        verify(notificationService).notifyUser(
                eq(requester.getId()),
                eq(addressee.getId()),
                eq(NotificationType.FRIEND_REQUEST_ACCEPTED),
                eq("Demande d'ami acceptee"),
                eq("Bob a accepte ta demande d'ami."),
                eq(Map.of(
                        "friendshipId", friendship.getId().toString(),
                        "friendUserId", addressee.getId().toString()
                )),
                eq("FRIENDSHIP"),
                eq(friendship.getId())
        );
    }

    @Test
    void searchFriendCandidatesRequiresAtLeastThreeCharacters() {
        AppUser currentUser = user(UUID.randomUUID(), "Alice");

        assertThatThrownBy(() -> friendService.searchFriendCandidates(currentUser, "al", PageRequest.of(0, 20)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("La recherche doit contenir au moins 3 caracteres.");

        verify(userRepository, never()).searchFriendCandidates(any(), any(), any());
    }

    @Test
    void searchDepartmentPeopleAndFriendsUsesCurrentUserDepartmentAndQuery() {
        AppUser currentUser = user(UUID.randomUUID(), "Alice");
        UUID departmentId = UUID.randomUUID();
        currentUser.setDepartment(departmentId);
        AppUser departmentUser = user(UUID.randomUUID(), "Bob");
        PageRequest pageable = PageRequest.of(0, 20);

        when(userRepository.searchDepartmentPeopleAndFriends(currentUser.getId(), departmentId, "bob", pageable))
                .thenReturn(new PageImpl<>(java.util.List.of(departmentUser), pageable, 1));
        when(friendshipRepository.findRelationsWithCandidates(currentUser.getId(), java.util.List.of(departmentUser.getId())))
                .thenReturn(java.util.List.of());

        assertThat(friendService.searchDepartmentPeopleAndFriends(currentUser, " bob ", pageable).getContent())
                .hasSize(1)
                .first()
                .extracting(result -> result.user().id())
                .isEqualTo(departmentUser.getId());
    }

    @Test
    void getFriendProfileReturnsAcceptedFriendProgress() {
        AppUser currentUser = user(UUID.randomUUID(), "Alice");
        AppUser friend = user(UUID.randomUUID(), "Bob");
        UUID departmentId = UUID.randomUUID();
        friend.setDepartment(departmentId);
        Friendship friendship = friendship(currentUser, friend, FriendshipStatus.ACCEPTED);
        AppUser commonFriend = user(UUID.randomUUID(), "Carol");
        Department department = new Department();
        department.setShortName("ITI");
        Challenge challenge = new Challenge();
        challenge.setId(UUID.randomUUID());
        ChallengeSubmission validatedSubmission = new ChallengeSubmission();
        validatedSubmission.setChallenge(challenge);
        validatedSubmission.setUser(friend);
        validatedSubmission.setStatus("VALIDATED");
        LeaderboardConfig config = new LeaderboardConfig();
        LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
        LeaderboardEntry entry = new LeaderboardEntry();
        entry.setRank(4);

        when(friendshipRepository.findBetween(currentUser.getId(), friend.getId())).thenReturn(Optional.of(friendship));
        when(friendshipRepository.findByUserIdAndStatus(currentUser.getId(), FriendshipStatus.ACCEPTED))
                .thenReturn(java.util.List.of(friendship(currentUser, commonFriend, FriendshipStatus.ACCEPTED)));
        when(friendshipRepository.findByUserIdAndStatus(friend.getId(), FriendshipStatus.ACCEPTED))
                .thenReturn(java.util.List.of(friendship(friend, commonFriend, FriendshipStatus.ACCEPTED)));
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(cardRepository.countPersonalCollectionCards()).thenReturn(4L);
        when(cardCollectionRepository.countOwnedEnabledCards(friend.getId(), CardCollectionType.VIRTUAL)).thenReturn(1L);
        when(cardCollectionRepository.countOwnedEnabledCards(friend.getId(), CardCollectionType.PHYSICAL)).thenReturn(2L);
        when(challengeRepository.findActiveChallengesForDepartment(departmentId)).thenReturn(java.util.List.of(challenge));
        when(submissionRepository.findValidatedChallengeIdsByUserIdOrParticipantUserId(friend.getId()))
                .thenReturn(java.util.List.of(challenge.getId()));
        when(leaderboardConfigRepository.findByScopeAndMetric(LeaderboardScope.USER, LeaderboardMetric.CHALLENGE_POINTS))
                .thenReturn(Optional.of(config));
        when(leaderboardSnapshotRepository.findFirstByConfigOrderByReleasedAtDesc(config)).thenReturn(Optional.of(snapshot));
        when(leaderboardEntryRepository.findBySnapshotAndSubjectId(snapshot, friend.getId())).thenReturn(Optional.of(entry));

        FriendProfileDto profile = friendService.getFriendProfile(currentUser, friend.getId());

        assertThat(profile.fullName()).isEqualTo("Bob");
        assertThat(profile.department()).isEqualTo("ITI");
        assertThat(profile.commonFriendsCount()).isEqualTo(1L);
        assertThat(profile.virtualCardsCompletionPercent()).isEqualTo(25.0);
        assertThat(profile.physicalCardsCompletionPercent()).isEqualTo(50.0);
        assertThat(profile.challengesCompletionPercent()).isEqualTo(100.0);
        assertThat(profile.challengePointsRank()).isEqualTo(4);
    }

    /**
     * La fiche d'un ami dit sa filiere et, s'il y a lieu, qu'il est apprenti.
     *
     * <p>Son departement reste celui de sa filiere : c'est lui qui colore la fiche et
     * qui choisit sa planete. La marque s'ajoute a cote, elle ne la remplace pas.</p>
     */
    @Test
    void getFriendProfileMarksApprentices() {
        AppUser currentUser = user(UUID.randomUUID(), "Alice");
        AppUser friend = user(UUID.randomUUID(), "Bob");
        friend.setPerf(true);
        Friendship friendship = friendship(currentUser, friend, FriendshipStatus.ACCEPTED);

        when(friendshipRepository.findBetween(currentUser.getId(), friend.getId()))
                .thenReturn(Optional.of(friendship));

        FriendProfileDto profile = friendService.getFriendProfile(currentUser, friend.getId());

        assertThat(profile.perf()).isTrue();
    }

    @Test
    void getFriendProfileRejectsNonFriends() {
        AppUser currentUser = user(UUID.randomUUID(), "Alice");
        UUID targetUserId = UUID.randomUUID();

        when(friendshipRepository.findBetween(currentUser.getId(), targetUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> friendService.getFriendProfile(currentUser, targetUserId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Ami introuvable.");
    }

    private static Friendship friendship(AppUser requester, AppUser addressee, FriendshipStatus status) {
        Friendship friendship = new Friendship();
        friendship.setRequester(requester);
        friendship.setAddressee(addressee);
        friendship.setStatus(status);
        return friendship;
    }

    private static AppUser user(UUID id, String displayName) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setKeycloakSub(id.toString());
        user.setEmail(displayName.toLowerCase() + "@example.com");
        user.setDisplayName(displayName);
        return user;
    }
}
