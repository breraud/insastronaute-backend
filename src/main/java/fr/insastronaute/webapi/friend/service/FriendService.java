package fr.insastronaute.webapi.friend.service;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.repository.ChallengeRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.friend.domain.Friendship;
import fr.insastronaute.webapi.friend.domain.FriendshipStatus;
import fr.insastronaute.webapi.friend.dto.FriendDto;
import fr.insastronaute.webapi.friend.dto.FriendRelationStatus;
import fr.insastronaute.webapi.friend.dto.FriendRequestDto;
import fr.insastronaute.webapi.friend.dto.FriendProfileDto;
import fr.insastronaute.webapi.friend.dto.FriendSearchResultDto;
import fr.insastronaute.webapi.friend.dto.FriendUserDto;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import fr.insastronaute.webapi.notification.domain.NotificationType;
import fr.insastronaute.webapi.notification.service.NotificationService;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardEntry;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardMetric;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardScope;
import fr.insastronaute.webapi.leaderboard.repository.LeaderboardConfigRepository;
import fr.insastronaute.webapi.leaderboard.repository.LeaderboardEntryRepository;
import fr.insastronaute.webapi.leaderboard.repository.LeaderboardSnapshotRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FriendService {

    private static final String FRIEND_REQUEST_SOURCE = "FRIEND_REQUEST";

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final DepartmentRepository departmentRepository;
    private final CardRepository cardRepository;
    private final CardCollectionRepository cardCollectionRepository;
    private final ChallengeRepository challengeRepository;
    private final ChallengeSubmissionRepository submissionRepository;
    private final LeaderboardConfigRepository leaderboardConfigRepository;
    private final LeaderboardSnapshotRepository leaderboardSnapshotRepository;
    private final LeaderboardEntryRepository leaderboardEntryRepository;
    private final AutoChallengeTriggerPublisher autoChallengeTriggerPublisher;

    public FriendService(
            FriendshipRepository friendshipRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            DepartmentRepository departmentRepository,
            CardRepository cardRepository,
            CardCollectionRepository cardCollectionRepository,
            ChallengeRepository challengeRepository,
            ChallengeSubmissionRepository submissionRepository,
            LeaderboardConfigRepository leaderboardConfigRepository,
            LeaderboardSnapshotRepository leaderboardSnapshotRepository,
            LeaderboardEntryRepository leaderboardEntryRepository,
            AutoChallengeTriggerPublisher autoChallengeTriggerPublisher
    ) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.departmentRepository = departmentRepository;
        this.cardRepository = cardRepository;
        this.cardCollectionRepository = cardCollectionRepository;
        this.challengeRepository = challengeRepository;
        this.submissionRepository = submissionRepository;
        this.leaderboardConfigRepository = leaderboardConfigRepository;
        this.leaderboardSnapshotRepository = leaderboardSnapshotRepository;
        this.leaderboardEntryRepository = leaderboardEntryRepository;
        this.autoChallengeTriggerPublisher = autoChallengeTriggerPublisher;
    }

    @Transactional(readOnly = true)
    public Page<FriendSearchResultDto> searchFriendCandidates(AppUser currentUser, String query, Pageable pageable) {
        String normalizedQuery = normalizeSearchQuery(query);
        Page<AppUser> users = userRepository.searchFriendCandidates(currentUser.getId(), normalizedQuery, pageable);
        return toSearchResults(currentUser, users);
    }

    @Transactional(readOnly = true)
    public Page<FriendSearchResultDto> searchDepartmentPeopleAndFriends(AppUser currentUser, String query, Pageable pageable) {
        String normalizedQuery = normalizeSearchQuery(query);
        Page<AppUser> users = userRepository.searchDepartmentPeopleAndFriends(
                currentUser.getId(),
                currentUser.getDepartment(),
                normalizedQuery,
                pageable
        );
        return toSearchResults(currentUser, users);
    }

    private Page<FriendSearchResultDto> toSearchResults(AppUser currentUser, Page<AppUser> users) {
        List<UUID> candidateIds = users.stream().map(AppUser::getId).toList();

        Map<UUID, Friendship> relationships = candidateIds.isEmpty()
                ? Map.of()
                : friendshipRepository.findRelationsWithCandidates(currentUser.getId(), candidateIds)
                        .stream()
                        .collect(Collectors.toMap(
                                friendship -> otherUserId(friendship, currentUser.getId()),
                                Function.identity()
                        ));

        return users.map(user -> new FriendSearchResultDto(
                FriendUserDto.from(user),
                relationStatus(relationships.get(user.getId()), currentUser.getId())
        ));
    }

    private String normalizeSearchQuery(String query) {
        if (!StringUtils.hasText(query)) {
            throw new BadRequestException(
                    "La recherche doit contenir au moins 3 caracteres.",
                    "query",
                    "La recherche doit contenir au moins 3 caracteres."
            );
        }

        String normalizedQuery = query.trim();
        if (normalizedQuery.length() < 3) {
            throw new BadRequestException(
                    "La recherche doit contenir au moins 3 caracteres.",
                    "query",
                    "La recherche doit contenir au moins 3 caracteres."
            );
        }

        return normalizedQuery;
    }

    @Transactional(readOnly = true)
    public List<FriendDto> listFriends(AppUser currentUser) {
        return friendshipRepository.findByUserIdAndStatus(currentUser.getId(), FriendshipStatus.ACCEPTED)
                .stream()
                .map(friendship -> FriendDto.from(friendship, currentUser))
                .toList();
    }

    @Transactional(readOnly = true)
    public FriendProfileDto getFriendProfile(AppUser currentUser, UUID friendUserId) {
        Friendship friendship = friendshipRepository.findBetween(currentUser.getId(), friendUserId)
                .filter(relation -> relation.getStatus() == FriendshipStatus.ACCEPTED)
                .orElseThrow(() -> new NotFoundException("Ami introuvable."));
        AppUser friend = otherUser(friendship, currentUser.getId());
        // Le meme total que l'ecran de collection du joueur, carte bonus exclue :
        // deux pourcentages qui se comparent doivent se compter pareil.
        long totalEnabledCards = cardRepository.countPersonalCollectionCards();
        Set<UUID> completedChallengeIds = Set.copyOf(
                submissionRepository.findValidatedChallengeIdsByUserIdOrParticipantUserId(friend.getId())
        );

        return new FriendProfileDto(
                displayName(friend),
                departmentName(friend),
                commonFriendsCount(currentUser, friend),
                percentage(cardCollectionRepository.countOwnedEnabledCards(friend.getId(), CardCollectionType.VIRTUAL), totalEnabledCards),
                percentage(cardCollectionRepository.countOwnedEnabledCards(friend.getId(), CardCollectionType.PHYSICAL), totalEnabledCards),
                challengeCompletionPercent(friend, completedChallengeIds),
                challengePointsRank(friend.getId()),
                friend.isPerf()
        );
    }

    @Transactional(readOnly = true)
    public Page<FriendRequestDto> listIncomingRequests(AppUser currentUser, Pageable pageable) {
        return friendshipRepository.findIncomingRequests(currentUser.getId(), pageable)
                .map(FriendRequestDto::from);
    }

    @Transactional(readOnly = true)
    public Page<FriendRequestDto> listOutgoingRequests(AppUser currentUser, Pageable pageable) {
        return friendshipRepository.findOutgoingRequests(currentUser.getId(), pageable)
                .map(FriendRequestDto::from);
    }

    @Transactional
    public FriendRequestDto sendRequest(AppUser currentUser, UUID targetUserId) {
        if (currentUser.getId().equals(targetUserId)) {
            throw new BadRequestException("Vous ne pouvez pas vous ajouter vous-meme en ami.");
        }

        AppUser targetUser = findUser(targetUserId);
        friendshipRepository.findBetween(currentUser.getId(), targetUserId)
                .ifPresent(this::rejectDuplicateRelationship);

        Friendship friendship = new Friendship();
        friendship.setRequester(currentUser);
        friendship.setAddressee(targetUser);
        friendship.setStatus(FriendshipStatus.PENDING);

        Friendship savedFriendship = saveNewFriendship(friendship);
        notificationService.notifyUser(
                targetUser.getId(),
                currentUser.getId(),
                NotificationType.FRIEND_REQUEST_RECEIVED,
                "Nouvelle demande d'ami",
                displayName(currentUser) + " veut t'ajouter en ami.",
                Map.of(
                        "friendRequestId", savedFriendship.getId().toString(),
                        "requesterUserId", currentUser.getId().toString()
                ),
                FRIEND_REQUEST_SOURCE,
                savedFriendship.getId()
        );

        return FriendRequestDto.from(savedFriendship);
    }

    @Transactional
    public FriendRequestDto acceptRequest(AppUser currentUser, UUID requestId) {
        Friendship friendship = findDetailedFriendship(requestId);
        requirePendingAddressee(friendship, currentUser);

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        // Both sides gained a friend, so both are re-evaluated.
        autoChallengeTriggerPublisher.publish(friendship.getRequester().getId(), AutoChallengeTrigger.FRIEND_ACCEPTED);
        autoChallengeTriggerPublisher.publish(friendship.getAddressee().getId(), AutoChallengeTrigger.FRIEND_ACCEPTED);
        friendship.setRespondedAt(fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
        Friendship savedFriendship = friendshipRepository.save(friendship);

        notificationService.notifyUser(
                savedFriendship.getRequester().getId(),
                currentUser.getId(),
                NotificationType.FRIEND_REQUEST_ACCEPTED,
                "Demande d'ami acceptee",
                displayName(currentUser) + " a accepte ta demande d'ami.",
                Map.of(
                        "friendshipId", savedFriendship.getId().toString(),
                        "friendUserId", currentUser.getId().toString()
                ),
                "FRIENDSHIP",
                savedFriendship.getId()
        );

        return FriendRequestDto.from(savedFriendship);
    }

    @Transactional
    public void declineRequest(AppUser currentUser, UUID requestId) {
        Friendship friendship = findDetailedFriendship(requestId);
        requirePendingAddressee(friendship, currentUser);
        friendshipRepository.delete(friendship);
    }

    @Transactional
    public void cancelRequest(AppUser currentUser, UUID requestId) {
        Friendship friendship = findDetailedFriendship(requestId);
        if (!friendship.getRequester().getId().equals(currentUser.getId()) || friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new NotFoundException("Demande d'ami introuvable.");
        }
        friendshipRepository.delete(friendship);
    }

    @Transactional
    public void removeFriend(AppUser currentUser, UUID friendUserId) {
        Friendship friendship = friendshipRepository.findBetween(currentUser.getId(), friendUserId)
                .orElseThrow(() -> new NotFoundException("Ami introuvable."));

        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new NotFoundException("Ami introuvable.");
        }

        friendshipRepository.delete(friendship);
    }

    private Friendship saveNewFriendship(Friendship friendship) {
        try {
            return friendshipRepository.save(friendship);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Une relation existe deja avec cet utilisateur.");
        }
    }

    private String departmentName(AppUser user) {
        if (user.getDepartment() == null) {
            return null;
        }
        return departmentRepository.findById(user.getDepartment()).map(Department::getShortName).orElse(null);
    }

    private long commonFriendsCount(AppUser firstUser, AppUser secondUser) {
        Set<UUID> firstFriendIds = friendIds(firstUser);
        return friendIds(secondUser).stream().filter(firstFriendIds::contains).count();
    }

    private Set<UUID> friendIds(AppUser user) {
        return friendshipRepository.findByUserIdAndStatus(user.getId(), FriendshipStatus.ACCEPTED).stream()
                .map(friendship -> otherUserId(friendship, user.getId()))
                .collect(Collectors.toSet());
    }

    private double challengeCompletionPercent(AppUser user, Set<UUID> completedChallengeIds) {
        if (user.getDepartment() == null) {
            return 0.0;
        }
        List<Challenge> accessibleChallenges = challengeRepository.findActiveChallengesForDepartment(user.getDepartment()).stream()
                .filter(challenge -> challenge.getPrerequisiteChallenges().stream()
                        .allMatch(prerequisite -> completedChallengeIds.contains(prerequisite.getId())))
                .toList();
        long completedCount = accessibleChallenges.stream()
                .filter(challenge -> completedChallengeIds.contains(challenge.getId()))
                .count();
        return percentage(completedCount, accessibleChallenges.size());
    }

    private Integer challengePointsRank(UUID userId) {
        return leaderboardConfigRepository.findByScopeAndMetric(LeaderboardScope.USER, LeaderboardMetric.CHALLENGE_POINTS)
                .flatMap(leaderboardSnapshotRepository::findFirstByConfigOrderByReleasedAtDesc)
                .flatMap(snapshot -> leaderboardEntryRepository.findBySnapshotAndSubjectId(snapshot, userId))
                .map(LeaderboardEntry::getRank)
                .orElse(null);
    }

    private static double percentage(long completed, long total) {
        return total == 0 ? 0.0 : completed * 100.0 / total;
    }

    private static AppUser otherUser(Friendship friendship, UUID currentUserId) {
        return friendship.getRequester().getId().equals(currentUserId)
                ? friendship.getAddressee()
                : friendship.getRequester();
    }

    private AppUser findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
    }

    private Friendship findDetailedFriendship(UUID requestId) {
        return friendshipRepository.findDetailedById(requestId)
                .orElseThrow(() -> new NotFoundException("Demande d'ami introuvable."));
    }

    private void requirePendingAddressee(Friendship friendship, AppUser currentUser) {
        if (friendship.getStatus() != FriendshipStatus.PENDING
                || !friendship.getAddressee().getId().equals(currentUser.getId())) {
            throw new NotFoundException("Demande d'ami introuvable.");
        }
    }

    private void rejectDuplicateRelationship(Friendship friendship) {
        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            throw new ConflictException("Vous etes deja amis.");
        }
        throw new ConflictException("Une demande d'ami est deja en attente.");
    }

    private FriendRelationStatus relationStatus(Friendship friendship, UUID currentUserId) {
        if (friendship == null) {
            return FriendRelationStatus.NONE;
        }
        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            return FriendRelationStatus.FRIEND;
        }
        return friendship.getRequester().getId().equals(currentUserId)
                ? FriendRelationStatus.PENDING_SENT
                : FriendRelationStatus.PENDING_RECEIVED;
    }

    private static UUID otherUserId(Friendship friendship, UUID currentUserId) {
        return friendship.getRequester().getId().equals(currentUserId)
                ? friendship.getAddressee().getId()
                : friendship.getRequester().getId();
    }

    private static String displayName(AppUser user) {
        return StringUtils.hasText(user.getDisplayName()) ? user.getDisplayName() : user.getEmail();
    }
}
