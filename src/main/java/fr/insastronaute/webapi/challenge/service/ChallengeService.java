package fr.insastronaute.webapi.challenge.service;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeTriggerPublisher;
import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeCategory;
import fr.insastronaute.webapi.challenge.auto.registry.AutoChallengeTypeRegistry;
import fr.insastronaute.webapi.challenge.domain.ChallengeFavorite;
import fr.insastronaute.webapi.challenge.domain.ChallengeGroupInvite;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmissionMessage;
import fr.insastronaute.webapi.challenge.dto.AdminChallengeDto;
import fr.insastronaute.webapi.challenge.dto.AdminChallengePageDto;
import fr.insastronaute.webapi.challenge.dto.AdminChallengeOptionDto;
import fr.insastronaute.webapi.challenge.dto.ChallengeBulkUpdateDto;
import fr.insastronaute.webapi.challenge.dto.ChallengeBulkUpdateResultDto;
import fr.insastronaute.webapi.challenge.dto.ChallengeDeletionImpactDto;
import fr.insastronaute.webapi.challenge.dto.DeletedChallengeDto;
import fr.insastronaute.webapi.challenge.dto.ChallengeCreateDto;
import fr.insastronaute.webapi.challenge.dto.ChallengeSubmissionDto;
import fr.insastronaute.webapi.challenge.dto.PendingSubmissionDto;
import fr.insastronaute.webapi.challenge.dto.ProofMediaDto;
import fr.insastronaute.webapi.challenge.dto.PlayerSubmissionDetailDto;
import fr.insastronaute.webapi.challenge.dto.ParticipantEligibilityDto;
import fr.insastronaute.webapi.challenge.dto.ChallengeRecommendationDto;
import fr.insastronaute.webapi.challenge.dto.ChallengeRecommendationReason;
import fr.insastronaute.webapi.challenge.dto.PlayerChallengeDto;
import fr.insastronaute.webapi.challenge.dto.ModerationParticipantCandidateDto;
import fr.insastronaute.webapi.challenge.dto.SubmissionParticipantDto;
import fr.insastronaute.webapi.challenge.dto.ModerationSubmissionChallengeDto;
import fr.insastronaute.webapi.challenge.dto.GroupInviteCodeDto;
import fr.insastronaute.webapi.challenge.dto.GroupInvitePreviewDto;
import fr.insastronaute.webapi.challenge.dto.ModerationSubmissionDetailDto;
import fr.insastronaute.webapi.challenge.dto.SubmissionMessageCreateDto;
import fr.insastronaute.webapi.challenge.dto.SubmissionMessageDto;
import fr.insastronaute.webapi.challenge.dto.SubmissionParticipantDto;
import fr.insastronaute.webapi.challenge.dto.SubmissionResultDto;
import fr.insastronaute.webapi.challenge.repository.ChallengeCategoryRepository;
import fr.insastronaute.webapi.challenge.domain.ChallengeGrace;
import fr.insastronaute.webapi.challenge.repository.ChallengeFavoriteRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeGraceRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionMessageRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository.ParticipantConfirmationProjection;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.service.AdminActionJournal;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeProgress;
import fr.insastronaute.webapi.challenge.auto.event.AutoChallengeSavedEvent;
import fr.insastronaute.webapi.challenge.dto.ChallengeProgressDto;
import fr.insastronaute.webapi.common.json.JsonPayloads;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.ForbiddenException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.notification.domain.NotificationType;
import fr.insastronaute.webapi.notification.service.NotificationService;
import fr.insastronaute.webapi.proof.domain.ProofType;
import fr.insastronaute.webapi.proof.service.ProofStorageService;
import fr.insastronaute.webapi.proof.service.ProofMediaStream;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.department.repository.DepartmentVerificationLinkRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import fr.insastronaute.webapi.leaderboard.service.LeaderboardService;
import fr.insastronaute.webapi.challenge.dto.ChallengeDepartmentShareDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import org.springframework.data.domain.PageRequest;
import java.util.Comparator;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import java.util.List;
import java.util.Map;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import fr.insastronaute.webapi.challenge.audit.ModerationAuditService;
import fr.insastronaute.webapi.challenge.audit.MediaReadAuditPublisher;

@Service
public class ChallengeService {

    private fr.insastronaute.webapi.economy.service.CurrencyLedger currencyLedger =
            fr.insastronaute.webapi.economy.service.CurrencyLedger.NOOP;

    /** Par setter : voir CurrencyLedger. Spring refuse de demarrer sans implementation. */
    @org.springframework.beans.factory.annotation.Autowired
    void setCurrencyLedger(fr.insastronaute.webapi.economy.service.CurrencyLedger currencyLedger) {
        this.currencyLedger = currencyLedger;
    }

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ChallengeService.class);

    private static final Duration REVIEW_LOCK_TTL = Duration.ofMinutes(15);
    private static final Duration REVIEW_LOCK_MAX_DURATION = Duration.ofMinutes(60);
    private static final String VALIDATION_MODE_AUTO = "AUTO";
    private static final String STATUS_WAITING_CONFIRMATION = "WAITING_CONFIRMATION";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_VALIDATED = "VALIDATED";
    private static final String STATUS_REJECTED = "REJECTED";

    /**
     * Le commentaire de moderation que pose l'annulation d'une soumission de
     * groupe par son initiateur.
     *
     * <p>Publique, et pour une raison precise : ce geste met la soumission en
     * REJECTED sans qu'aucun administrateur n'intervienne et sans laisser de
     * trace de moderation. La reprise du registre doit donc pouvoir le
     * reconnaitre pour ne pas le compter parmi les decisions d'administration
     * dont la trace manquerait. Une seconde copie de la chaine serait une
     * seconde occasion de la laisser diverger.</p>
     */
    public static final String GROUP_SUBMISSION_CANCELLED_BY_INITIATOR =
            "Soumission de groupe annulée par l'initiateur.";

    /** Ce que lit un joueur dont la preuve est retirée parce que le défi est passé en automatique. */
    public static final String AUTOMATIC_SWITCH_COMMENT =
            "Ce défi se valide désormais automatiquement : ta preuve n'est plus nécessaire.";

    /**
     * Le commentaire de moderation que pose le refus d'une invitation de
     * groupe par un membre. Publique pour la meme raison que
     * {@link #GROUP_SUBMISSION_CANCELLED_BY_INITIATOR}.
     */
    public static final String GROUP_INVITE_DECLINED_BY_MEMBER =
            "Participation refusée par un membre du groupe.";

    /** Dit une fois : la meme phrase sert de motif de refus et de drapeau. */
    private static final String ALREADY_IN_THE_GROUP = "Tu fais déjà partie de cette équipe.";

    /** Quinze minutes : le temps d'un code qu'on montre a quelqu'un assis a cote. */
    private static final Duration GROUP_INVITE_LIFETIME = Duration.ofMinutes(15);

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private fr.insastronaute.webapi.challenge.repository.ChallengeGroupInviteRepository groupInviteRepository;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private fr.insastronaute.webapi.common.security.config.AppSecurityProperties securityProperties;
    /** Le courtier qui porte deja la fresque, les combats et la cloche. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    private final ChallengeRepository challengeRepository;
    private final ChallengeCategoryRepository categoryRepository;
    private final ChallengeFavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final ChallengeSubmissionRepository submissionRepository;
    private final ChallengeSubmissionMessageRepository submissionMessageRepository;
    private final DepartmentRepository departmentRepository;
    private final DepartmentVerificationLinkRepository verificationLinkRepository;
    private final ProofStorageService proofStorageService;
    private final NotificationService notificationService;
    private final AutoChallengeTypeRegistry autoChallengeTypeRegistry;
    private final ModerationAuditService moderationAuditService;
    private final MediaReadAuditPublisher mediaReadAuditPublisher;
    private final AutoChallengeTriggerPublisher autoChallengeTriggerPublisher;
    private final AdminActionJournal journal;

    private final ChallengeVisibilityService challengeVisibility;
    private final FriendshipRepository friendshipRepository;
    private final org.springframework.context.ApplicationEventPublisher applicationEventPublisher;
    private final LeaderboardService leaderboardService;
    private final ChallengeGraceRepository graceRepository;

    ChallengeService(
            ChallengeRepository challengeRepository,
            ChallengeCategoryRepository categoryRepository,
            ChallengeFavoriteRepository favoriteRepository,
            UserRepository userRepository,
            ChallengeSubmissionRepository submissionRepository,
            ChallengeSubmissionMessageRepository submissionMessageRepository,
            DepartmentRepository departmentRepository,
            DepartmentVerificationLinkRepository verificationLinkRepository,
            ProofStorageService proofStorageService,
            NotificationService notificationService,
            AutoChallengeTypeRegistry autoChallengeTypeRegistry,
            ModerationAuditService moderationAuditService,
            MediaReadAuditPublisher mediaReadAuditPublisher,
            AutoChallengeTriggerPublisher autoChallengeTriggerPublisher,
            AdminActionJournal journal,
            ChallengeVisibilityService challengeVisibility,
            FriendshipRepository friendshipRepository,
            org.springframework.context.ApplicationEventPublisher applicationEventPublisher) {
        this(challengeRepository, categoryRepository, favoriteRepository, userRepository, submissionRepository,
                submissionMessageRepository, departmentRepository, verificationLinkRepository, proofStorageService,
                notificationService, autoChallengeTypeRegistry, moderationAuditService, mediaReadAuditPublisher,
                autoChallengeTriggerPublisher, journal, challengeVisibility, friendshipRepository,
                applicationEventPublisher, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ChallengeService(
            ChallengeRepository challengeRepository,
            ChallengeCategoryRepository categoryRepository,
            ChallengeFavoriteRepository favoriteRepository,
            UserRepository userRepository,
            ChallengeSubmissionRepository submissionRepository,
            ChallengeSubmissionMessageRepository submissionMessageRepository,
            DepartmentRepository departmentRepository,
            DepartmentVerificationLinkRepository verificationLinkRepository,
            ProofStorageService proofStorageService,
            NotificationService notificationService,
            AutoChallengeTypeRegistry autoChallengeTypeRegistry,
            ModerationAuditService moderationAuditService,
            MediaReadAuditPublisher mediaReadAuditPublisher,
            AutoChallengeTriggerPublisher autoChallengeTriggerPublisher,
            AdminActionJournal journal,
            ChallengeVisibilityService challengeVisibility,
            FriendshipRepository friendshipRepository,
            org.springframework.context.ApplicationEventPublisher applicationEventPublisher,
            LeaderboardService leaderboardService,
            ChallengeGraceRepository graceRepository) {
        this.challengeRepository = challengeRepository;
        this.categoryRepository = categoryRepository;
        this.favoriteRepository = favoriteRepository;
        this.userRepository = userRepository;
        this.submissionRepository = submissionRepository;
        this.submissionMessageRepository = submissionMessageRepository;
        this.departmentRepository = departmentRepository;
        this.verificationLinkRepository = verificationLinkRepository;
        this.proofStorageService = proofStorageService;
        this.notificationService = notificationService;
        this.autoChallengeTypeRegistry = autoChallengeTypeRegistry;
        this.moderationAuditService = moderationAuditService;
        this.mediaReadAuditPublisher = mediaReadAuditPublisher;
        this.autoChallengeTriggerPublisher = autoChallengeTriggerPublisher;
        this.journal = journal;
        this.challengeVisibility = challengeVisibility;
        this.friendshipRepository = friendshipRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.leaderboardService = leaderboardService;
        this.graceRepository = graceRepository;
    }

    @Transactional(readOnly = true)
    public List<ChallengeDepartmentShareDto> getDepartmentShares(UUID challengeId) {
        if (leaderboardService == null) {
            return List.of();
        }
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new NotFoundException("Defi introuvable."));
        return calculateDepartmentShares(challenge,
                submissionRepository.findValidatedSubmissionsForChallenge(challengeId),
                leaderboardService.currentDepartmentCoefficients(), departmentsById());
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<ChallengeDepartmentShareDto>> getAllDepartmentShares() {
        if (leaderboardService == null) {
            return Map.of();
        }
        Map<UUID, Double> coefficients = leaderboardService.currentDepartmentCoefficients();
        Map<UUID, Department> departments = departmentsById();
        return submissionRepository.findValidatedSubmissionsForPointRecalculation().stream()
                .collect(Collectors.groupingBy(submission -> submission.getChallenge().getId(), LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> calculateDepartmentShares(entry.getValue().getFirst().getChallenge(),
                                entry.getValue(), coefficients, departments),
                        (left, right) -> left, LinkedHashMap::new));
    }

    private Map<UUID, Department> departmentsById() {
        return departmentRepository.findAllByVirtualFalse().stream()
                .collect(Collectors.toMap(Department::getId, Function.identity()));
    }

    private List<ChallengeDepartmentShareDto> calculateDepartmentShares(
            Challenge challenge,
            List<ChallengeSubmission> submissions,
            Map<UUID, Double> coefficients,
            Map<UUID, Department> departments) {
        Map<UUID, Long> rawByDepartment = new HashMap<>();
        int rewardedSubmissions = 0;
        for (ChallengeSubmission submission : submissions) {
            Integer quota = challenge.getGlobalQuota();
            rewardedSubmissions++;
            if (quota != null && quota > 0 && rewardedSubmissions > quota) {
                continue;
            }
            long reward = (challenge.getPointsReward() == null ? 0L : challenge.getPointsReward())
                    + (submission.getBonusPoints() == null ? 0L : submission.getBonusPoints());
            List<AppUser> members = submissionMembers(submission);
            Map<UUID, Long> byUser = new HashMap<>();
            if (challenge.isRewardShared()) {
                shareReward(reward, members, byUser);
            } else {
                members.forEach(member -> byUser.merge(member.getId(), reward, Long::sum));
            }
            for (AppUser member : members) {
                if (member.getDepartment() == null || !leaderboardService.contributesToLeaderboards(member)) {
                    continue;
                }
                rawByDepartment.merge(member.getDepartment(), byUser.getOrDefault(member.getId(), 0L), Long::sum);
            }
        }

        Map<UUID, Double> weighted = rawByDepartment.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue() * coefficients.getOrDefault(entry.getKey(), 1d)));
        double total = weighted.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0d) {
            return List.of();
        }
        return weighted.entrySet().stream()
                .filter(entry -> departments.get(entry.getKey()) != null)
                .map(entry -> new ChallengeDepartmentShareDto(departments.get(entry.getKey()).getShortName(),
                        departments.get(entry.getKey()).getColor(),
                        Math.round(entry.getValue() * 1000000d / total) / 10000d))
                .sorted(Comparator.comparingDouble(ChallengeDepartmentShareDto::percentage).reversed()
                        .thenComparing(ChallengeDepartmentShareDto::department))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminChallengeDto> getAllChallenges() {
        return challengeRepository.findAll().stream()
                .map(this::toAdminChallengeDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminChallengePageDto getChallengesPage(
            String query, String status, String category, String validation, String sort, int requestedPage, int requestedSize) {
        int page = Math.max(0, requestedPage);
        int size = Math.max(1, Math.min(100, requestedSize));
        Instant now = TimeTravelContext.instantNow();
        List<Challenge> all = challengeRepository.findAll();
        String normalizedQuery = searchable(query);

        List<Challenge> filtered = all.stream()
                .filter(challenge -> normalizedQuery.isEmpty() || searchable(challengeSearchText(challenge)).contains(normalizedQuery))
                .filter(challenge -> blank(status) || status.equalsIgnoreCase(statusOf(challenge, now)))
                .filter(challenge -> blank(category)
                        || challenge.getCategories().stream().anyMatch(category::equalsIgnoreCase))
                .filter(challenge -> blank(validation) || validation.equalsIgnoreCase(challenge.getValidationMode()))
                .sorted(adminChallengeComparator(sort))
                .toList();

        int from = Math.min(page * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        int totalPages = filtered.isEmpty() ? 0 : (filtered.size() + size - 1) / size;
        return new AdminChallengePageDto(
                filtered.subList(from, to).stream().map(this::toAdminChallengeDto).toList(),
                page, size, filtered.size(), totalPages,
                all.stream().filter(challenge -> statusOf(challenge, now).equals("active")).count(),
                all.stream().filter(challenge -> statusOf(challenge, now).equals("draft")).count(),
                all.stream().filter(challenge -> statusOf(challenge, now).equals("scheduled")).count());
    }

    private static Comparator<Challenge> adminChallengeComparator(String sort) {
        return switch (sort == null ? "START_DATE_DESC" : sort) {
            case "START_DATE_ASC" -> Comparator.comparing(Challenge::getStartDate, Comparator.nullsLast(Comparator.naturalOrder()));
            case "POINTS_ASC" -> Comparator.comparing(Challenge::getPointsReward, Comparator.nullsLast(Comparator.naturalOrder()));
            case "POINTS_DESC" -> Comparator.comparing(Challenge::getPointsReward, Comparator.nullsLast(Comparator.reverseOrder()));
            default -> Comparator.comparing(Challenge::getStartDate, Comparator.nullsLast(Comparator.reverseOrder()));
        };
    }

    @Transactional(readOnly = true)
    public List<AdminChallengeOptionDto> getChallengeOptions() {
        return challengeRepository.findAll().stream()
                .sorted(Comparator.comparing(Challenge::getTitle, String.CASE_INSENSITIVE_ORDER))
                .map(challenge -> new AdminChallengeOptionDto(
                        challenge.getId(), challenge.getTitle(), challenge.getDescription()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UUID> getChallengeIds(String query, String status, String category, String validation) {
        Instant now = TimeTravelContext.instantNow();
        String normalizedQuery = searchable(query);

        return challengeRepository.findAll().stream()
                .filter(challenge -> normalizedQuery.isEmpty() || searchable(challengeSearchText(challenge)).contains(normalizedQuery))
                .filter(challenge -> blank(status) || status.equalsIgnoreCase(statusOf(challenge, now)))
                .filter(challenge -> blank(category)
                        || challenge.getCategories().stream().anyMatch(category::equalsIgnoreCase))
                .filter(challenge -> blank(validation) || validation.equalsIgnoreCase(challenge.getValidationMode()))
                .sorted(Comparator.comparing(Challenge::getStartDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(Challenge::getId)
                .toList();
    }

    private static String challengeSearchText(Challenge challenge) {
        String departments = challenge.getTargetDepartments().isEmpty()
                ? "global"
                : challenge.getTargetDepartments().stream()
                        .map(department -> department.getId() + " " + department.getShortName() + " " + department.getName())
                        .collect(Collectors.joining(" "));
        return challenge.getTitle() + " " + Objects.toString(challenge.getDescription(), "") + " "
                + String.join(" ", challenge.getCategories()) + " " + departments;
    }

    private static String statusOf(Challenge challenge, Instant now) {
        if (challenge.getStartDate() == null || challenge.getEndDate() == null) return "draft";
        if (now.isBefore(challenge.getStartDate())) return "scheduled";
        if (now.isAfter(challenge.getEndDate())) return "archived";
        return "active";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank() || value.equalsIgnoreCase("all");
    }

    private static String searchable(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    @Transactional(readOnly = true)
    public List<PlayerChallengeDto> getPlayerChallenges(String keycloakSub) {

        AppUser player = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Joueur non trouvé en base"));

        return visiblePlayerChallenges(player);
    }

    @Transactional(readOnly = true)
    public List<PlayerChallengeDto> getPlayerChallenges(UUID userId) {
        AppUser player = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Joueur non trouvé en base"));

        return visiblePlayerChallenges(player);
    }

    @Transactional(readOnly = true)
    public List<PlayerChallengeDto> getFavoriteChallenges(String keycloakSub) {
        AppUser player = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Joueur non trouvé en base"));

        return visiblePlayerChallenges(player).stream()
                .filter(PlayerChallengeDto::favorite)
                .toList();
    }

    @Transactional
    public void addFavoriteChallenge(String keycloakSub, UUID challengeId) {
        AppUser player = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Joueur non trouvé en base"));

        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new NotFoundException("Défi non trouvé"));

        if (!canPlayerSeeChallenge(player, challenge)) {
            throw new ForbiddenException("Ce défi n'est pas disponible pour ce joueur.");
        }

        if (favoriteRepository.existsByUserIdAndChallengeId(player.getId(), challengeId)) {
            return;
        }

        ChallengeFavorite favorite = new ChallengeFavorite();
        favorite.setUser(player);
        favorite.setChallenge(challenge);
        favoriteRepository.save(favorite);
    }

    @Transactional
    public void removeFavoriteChallenge(String keycloakSub, UUID challengeId) {
        AppUser player = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Joueur non trouvé en base"));

        favoriteRepository.deleteByUserIdAndChallengeId(player.getId(), challengeId);
    }

    @Transactional(readOnly = true)
    public List<SubmissionResultDto> getSubmissionResults(String keycloakSub) {
        AppUser player = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Joueur non trouvé en base"));

        List<ChallengeSubmission> submissions =
                submissionRepository.findByUserIdOrParticipantUserIdOrderByCreatedAtDesc(player.getId());
        Set<UUID> unrewarded = unrewardedSubmissionIds(submissions);

        return submissions.stream()
                .map(submission -> toSubmissionResultDto(submission, player, unrewarded))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubmissionResultDto> getSubmissionsWaitingConfirmation(String keycloakSub) {
        return getSubmissionResultsByStatus(keycloakSub, STATUS_WAITING_CONFIRMATION);
    }

    @Transactional(readOnly = true)
    public List<SubmissionResultDto> getSubmissionsPendingValidation(String keycloakSub) {
        return getSubmissionResultsByStatus(keycloakSub, STATUS_PENDING);
    }

    private List<SubmissionResultDto> getSubmissionResultsByStatus(String keycloakSub, String status) {
        AppUser player = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Joueur non trouvé en base"));

        List<ChallengeSubmission> submissions =
                submissionRepository.findByUserIdOrParticipantUserIdAndStatusOrderByCreatedAtDesc(
                        player.getId(),
                        status
                );
        Set<UUID> unrewarded = unrewardedSubmissionIds(submissions);

        return submissions.stream()
                .map(submission -> toSubmissionResultDto(submission, player, unrewarded))
                .toList();
    }

    /**
     * Les soumissions du lot qui ne rapportent rien parce que le quota du defi
     * etait deja atteint quand elles ont ete validees.
     *
     * <p>Une requete par defi a quota present dans le lot, et aucune dans le cas
     * courant ou aucun quota n'est pose. Jamais une par ligne : l'historique d'un
     * joueur en compte des dizaines.</p>
     *
     * <p>Le rang suit le meme ordre que {@link #recalculateChallengePoints()} --
     * validatedAt asc nulls last, createdAt asc, id asc -- sans quoi l'ecran
     * annoncerait un gain que le classement ne verse pas.</p>
     */
    Set<UUID> unrewardedSubmissionIds(List<ChallengeSubmission> submissions) {
        Map<UUID, Integer> quotaByChallenge = new HashMap<>();
        for (ChallengeSubmission submission : submissions) {
            if (!STATUS_VALIDATED.equals(submission.getStatus())) {
                continue;
            }
            Challenge challenge = submission.getChallenge();
            Integer quota = challenge.getGlobalQuota();
            if (quota != null && quota > 0) {
                quotaByChallenge.put(challenge.getId(), quota);
            }
        }
        if (quotaByChallenge.isEmpty()) {
            return Set.of();
        }

        Set<UUID> unrewarded = new HashSet<>();
        for (Map.Entry<UUID, Integer> entry : quotaByChallenge.entrySet()) {
            List<ChallengeSubmission> ranked =
                    submissionRepository.findValidatedSubmissionsForChallenge(entry.getKey());
            for (int rank = entry.getValue(); rank < ranked.size(); rank++) {
                unrewarded.add(ranked.get(rank).getId());
            }
        }
        return unrewarded;
    }

    private SubmissionResultDto toSubmissionResultDto(
            ChallengeSubmission submission, AppUser player, Set<UUID> unrewardedSubmissionIds) {
        boolean participantOnly = !submission.getUser().getId().equals(player.getId());
        Challenge challenge = submission.getChallenge();
        return new SubmissionResultDto(
                submission.getId(),
                challenge.getId(),
                challenge.getTitle(),
                submission.getStatus(),
                submission.getCreatedAt(),
                submission.getValidatedAt(),
                challenge.getPointsReward(),
                submission.getBonusPoints(),
                // Un gain automatique est une vraie soumission, sans preuve ni equipe :
                // c'est son proofType qui la distingue d'une preuve envoyee a la main.
                "AUTO".equals(submission.getProofType()),
                challenge.isPromo(),
                challenge.isRewardShared(),
                !unrewardedSubmissionIds.contains(submission.getId()),
                submission.getModerationComment(),
                participantOnly,
                participantOnly ? ownConfirmationStatus(submission, player) : null
        );
    }

    /**
     * Ce que ce joueur a repondu a son invitation.
     *
     * <p>Lu seulement pour les soumissions encore en attente : ailleurs, la question
     * ne se pose plus, et la lecture couterait une requete par ligne.</p>
     */
    private String ownConfirmationStatus(ChallengeSubmission submission, AppUser player) {
        if (!STATUS_WAITING_CONFIRMATION.equals(submission.getStatus())) {
            return null;
        }
        return submissionRepository.findParticipantConfirmations(submission.getId()).stream()
                .filter(participant -> player.getId().equals(participant.getUserId()))
                .map(ParticipantConfirmationProjection::getConfirmationStatus)
                .findFirst()
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public PlayerSubmissionDetailDto getPlayerSubmissionDetail(UUID submissionId, String keycloakSub) {
        AppUser player = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Joueur introuvable"));
        ChallengeSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Soumission introuvable"));
        ensureSubmissionMember(submission, player);

        return toPlayerSubmissionDetailDto(submission, player);
    }

    @Transactional
    public void cancelGroupSubmission(UUID submissionId, String keycloakSub) {
        AppUser player = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Joueur introuvable"));
        ChallengeSubmission submission = submissionRepository.findByIdForUpdate(submissionId)
                .orElseThrow(() -> new NotFoundException("Soumission introuvable"));

        ensureSubmissionSubmitter(submission, player);
        ensureWaitingGroupSubmission(submission);

        submission.setStatus(STATUS_REJECTED);
        submission.setModerationComment(GROUP_SUBMISSION_CANCELLED_BY_INITIATOR);
        submission.setValidatedAt(null);
        clearReviewLock(submission);
        submissionRepository.save(submission);
    }

    /**
     * Le code a montrer pour qu'on rejoigne son equipe en le scannant.
     *
     * <p>Le regenerer prolonge le meme code plutot que d'en ouvrir un second :
     * un code montre une fois ne doit pas rester une porte ouverte sur l'equipe.</p>
     */
    @Transactional
    public GroupInviteCodeDto createGroupInviteCode(String keycloakSub, UUID challengeId) {
        AppUser player = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Joueur introuvable"));
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new NotFoundException("Défi non trouvé"));

        if (challenge.getMaxGroupSize() == null || challenge.getMaxGroupSize() <= 1) {
            throw new BadRequestException("Ce défi ne se joue pas en groupe.");
        }
        validateChallengeActive(challenge, Set.of(player.getId()));

        ChallengeGroupInvite invite = groupInviteRepository
                .findByChallengeIdAndSubmitterId(challengeId, player.getId())
                .orElseGet(() -> {
                    ChallengeGroupInvite created = new ChallengeGroupInvite();
                    created.setChallenge(challenge);
                    created.setSubmitter(player);
                    return created;
                });
        invite.setExpiresAt(TimeTravelContext.instantNow().plus(GROUP_INVITE_LIFETIME));
        groupInviteRepository.save(invite);

        String joinUrl = groupInviteJoinUrl(invite.getToken());
        return new GroupInviteCodeDto(invite.getToken(), joinUrl, invite.getExpiresAt(), QrCodeSvg.of(joinUrl));
    }

    /**
     * Ce que voit celui qui vient de scanner, avant de dire oui.
     *
     * <p>Les raisons de refus se disent avec ses mots -- « tu as déjà validé ce
     * défi » plutot que « invitation invalide » : il vient de scanner un code qu'on
     * lui a tendu, il merite de savoir ce qui coince.</p>
     */
    @Transactional(readOnly = true)
    public GroupInvitePreviewDto previewGroupInvite(String keycloakSub, UUID token) {
        AppUser player = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Joueur introuvable"));
        ChallengeGroupInvite invite = groupInviteRepository.findByToken(token)
                .orElseThrow(() -> new NotFoundException("Ce code d'invitation n'existe pas."));

        Challenge challenge = invite.getChallenge();
        AppUser submitter = invite.getSubmitter();
        String reason = groupInviteBlocker(invite, player);
        boolean alreadyMember = reason != null && reason.equals(ALREADY_IN_THE_GROUP);

        return new GroupInvitePreviewDto(
                challenge.getId(),
                challenge.getTitle(),
                displayName(submitter),
                reason == null,
                alreadyMember,
                reason
        );
    }

    /**
     * Rejoindre une equipe en scannant son code.
     *
     * <p>Scanner vaut acceptation : la personne vient de dire oui, lui envoyer une
     * invitation a confirmer serait exactement la redondance que ce code supprime.</p>
     *
     * <p>Deux moments possibles. Si la preuve est deja partie, elle rejoint la
     * soumission en attente, deja d'accord -- et la preuve part si elle etait la
     * derniere attendue. Sinon, elle rejoint l'equipe en preparation : elle sera
     * comptee comme ayant accepte au moment de l'envoi.</p>
     */
    @Transactional
    public GroupInvitePreviewDto joinGroupInvite(String keycloakSub, UUID token) {
        AppUser player = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Joueur introuvable"));
        ChallengeGroupInvite invite = groupInviteRepository.findByToken(token)
                .orElseThrow(() -> new NotFoundException("Ce code d'invitation n'existe pas."));

        String blocker = groupInviteBlocker(invite, player);
        if (blocker != null) {
            throw new ConflictException(blocker);
        }

        Challenge challenge = invite.getChallenge();
        AppUser submitter = invite.getSubmitter();
        Optional<ChallengeSubmission> waiting = waitingGroupSubmissionOf(submitter, challenge);

        if (waiting.isPresent()) {
            ChallengeSubmission submission = waiting.get();
            Set<AppUser> participants = new LinkedHashSet<>(submission.getParticipants());
            participants.add(player);
            submission.setParticipants(participants);
            submissionRepository.saveAndFlush(submission);
            submissionRepository.updateParticipantConfirmation(submission.getId(), player.getId(), "ACCEPTED");
            notifyGroupInviteAnswer(submission, player, NotificationType.CHALLENGE_GROUP_INVITE_ACCEPTED);
            promoteIfGroupReady(submission);
            submissionRepository.save(submission);
            if (STATUS_VALIDATED.equals(submission.getStatus())) {
                recalculateChallengePoints();
            }
        } else {
            // L'equipe se compose encore : on la garde avec le brouillon de preuve,
            // la ou le chef la voit, et on retient que cette personne a dit oui.
            proofStorageService.addDraftParticipant(submitter, challenge, player);
        }

        invite.getJoinedUserIds().add(player.getId());
        groupInviteRepository.save(invite);
        announceTeamChange(submitter, challenge.getId(), waiting.map(ChallengeSubmission::getId).orElse(null));

        return new GroupInvitePreviewDto(
                challenge.getId(), challenge.getTitle(), displayName(submitter), false, true, null);
    }

    /** Ce qui empeche cette personne de rejoindre, dit avec ses mots. */
    private String groupInviteBlocker(ChallengeGroupInvite invite, AppUser player) {
        Challenge challenge = invite.getChallenge();
        AppUser submitter = invite.getSubmitter();

        if (invite.expired()) {
            return "Ce code d'invitation a expiré : demande à ton coéquipier d'en afficher un nouveau.";
        }
        if (submitter.getId().equals(player.getId())) {
            return "C'est ton propre code : montre-le à quelqu'un d'autre.";
        }

        Optional<ChallengeSubmission> waiting = waitingGroupSubmissionOf(submitter, challenge);
        if (waiting.isPresent()) {
            ChallengeSubmission submission = waiting.get();
            if (submissionMembers(submission).stream().anyMatch(member -> member.getId().equals(player.getId()))) {
                return ALREADY_IN_THE_GROUP;
            }
            if (1 + submission.getParticipants().size() >= challenge.getMaxGroupSize()) {
                return "L'équipe est déjà au complet pour ce défi.";
            }
        } else if (invite.getJoinedUserIds().contains(player.getId())) {
            return ALREADY_IN_THE_GROUP;
        }

        return participantBlocker(submitter, player, challenge);
    }

    /** L'envoi de groupe encore en attente de ce joueur sur ce defi, s'il y en a un. */
    private Optional<ChallengeSubmission> waitingGroupSubmissionOf(AppUser submitter, Challenge challenge) {
        return submissionRepository
                .findBlockingSubmissionsForUsersAndChallenge(List.of(submitter.getId()), challenge.getId())
                .stream()
                .filter(submission -> STATUS_WAITING_CONFIRMATION.equals(submission.getStatus()))
                .filter(submission -> submission.getUser().getId().equals(submitter.getId()))
                .findFirst();
    }

    /**
     * Dire au chef que son equipe a bouge.
     *
     * <p>Il a le panneau sous les yeux pendant que les gens scannent : sans ce mot,
     * il doit fermer la carte et la rouvrir pour voir qui est arrive.</p>
     *
     * <p>Le message ne porte que de quoi savoir quoi relire. Le detail se lit par
     * l'API, qui decide seule de ce que chacun a le droit de voir.</p>
     */
    private void announceTeamChange(AppUser submitter, UUID challengeId, UUID submissionId) {
        if (messagingTemplate == null || submitter == null || submitter.getKeycloakSub() == null) {
            return;
        }
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("challengeId", challengeId.toString());
            if (submissionId != null) {
                event.put("submissionId", submissionId.toString());
            }
            messagingTemplate.convertAndSendToUser(
                    submitter.getKeycloakSub(), "/queue/challenge-teams", event);
        } catch (RuntimeException unreachable) {
            // L'equipe a bien change en base : ne pas l'annoncer n'annule rien.
            LOGGER.warn("Changement d'equipe non annonce sur le defi {}.", challengeId, unreachable);
        }
    }

    private String groupInviteJoinUrl(UUID token) {
        String base = securityProperties == null ? "" : securityProperties.getFrontendBaseUrl();
        return base + "/rejoindre/" + token;
    }

    /**
     * Remplacer l'equipe d'un envoi de groupe deja parti.
     *
     * <p>Le chef compose sa liste dans la page -- il ajoute, il retire -- et
     * n'envoie qu'une fois decide. Un appel par geste aurait fait subir au groupe
     * des etats que personne ne voulait : retirer le dernier membre qui n'avait pas
     * repondu aurait envoye la preuve a l'instant meme, alors qu'il restait un
     * coequipier a ajouter.</p>
     *
     * <p>La liste recue fait foi : ce qui y entre est invite, ce qui en sort est
     * prevenu, et les reponses deja donnees sont conservees.</p>
     */
    @Transactional
    public PlayerSubmissionDetailDto replaceGroupParticipants(
            UUID submissionId, String keycloakSub, List<UUID> participantUserIds
    ) {
        AppUser player = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Joueur introuvable"));
        ChallengeSubmission submission = submissionRepository.findByIdForUpdate(submissionId)
                .orElseThrow(() -> new NotFoundException("Soumission introuvable"));

        ensureSubmissionSubmitter(submission, player);
        ensureWaitingGroupSubmission(submission);

        List<UUID> wanted = participantUserIds == null ? List.of() : participantUserIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.equals(player.getId()))
                .distinct()
                .toList();

        Map<UUID, AppUser> current = submission.getParticipants().stream()
                .collect(Collectors.toMap(AppUser::getId, member -> member, (a, b) -> a, LinkedHashMap::new));

        List<AppUser> resolved = userRepository.findAllById(wanted);
        if (resolved.size() != wanted.size()) {
            throw new NotFoundException("Coéquipier introuvable.");
        }

        Set<AppUser> finalGroup = new LinkedHashSet<>(resolved);
        validateGroupSize(finalGroup, submission.getChallenge());

        List<AppUser> added = resolved.stream().filter(member -> !current.containsKey(member.getId())).toList();
        List<AppUser> removed = current.values().stream()
                .filter(member -> wanted.stream().noneMatch(id -> id.equals(member.getId())))
                .toList();

        // Les nouveaux venus passent la meme porte que ceux de l'envoi initial :
        // prerequis, departement, soumission deja en cours ailleurs.
        for (AppUser newcomer : added) {
            String blocker = participantBlocker(submission.getUser(), newcomer, submission.getChallenge());
            if (blocker != null) {
                throw new BadRequestException(blocker, "participantUserIds", blocker);
            }
        }

        submission.setParticipants(finalGroup);
        submissionRepository.saveAndFlush(submission);

        added.forEach(newcomer -> notifyGroupInvite(submission, newcomer));
        removed.forEach(gone -> notifyRemovedFromGroup(submission, gone));

        promoteIfGroupReady(submission);
        submissionRepository.save(submission);
        if (STATUS_VALIDATED.equals(submission.getStatus())) {
            recalculateChallengePoints();
        }
        announceTeamChange(submission.getUser(), submission.getChallenge().getId(), submission.getId());
        return toPlayerSubmissionDetailDto(submission, player);
    }

    /**
     * Envoyer la preuve des que plus rien ne l'attend.
     *
     * <p>Deux conditions : tout le monde a repondu oui, et le groupe tient dans les
     * clous du defi. Le chef, lui, envoie ses changements d'un bloc -- il n'y a donc
     * jamais d'equipe a moitie composee a l'instant ou l'on regarde.</p>
     */
    private void promoteIfGroupReady(ChallengeSubmission submission) {
        if (belowMinimumGroupSize(submission)) {
            return;
        }
        submissionRepository.flush();
        if (submissionRepository.countParticipantsNotAccepted(submission.getId()) > 0) {
            return;
        }
        promoteConfirmedGroupSubmission(submission);
    }

    /** Sortir quelqu'un du groupe : la ligne de confirmation part avec lui. */
    private void removeParticipantFrom(ChallengeSubmission submission, AppUser participant) {
        Set<AppUser> remaining = new LinkedHashSet<>(submission.getParticipants());
        remaining.removeIf(member -> member.getId().equals(participant.getId()));
        submission.setParticipants(remaining);
        submissionRepository.saveAndFlush(submission);
    }

    /** L'invitation d'une seule personne, quand le groupe s'agrandit apres coup. */
    private void notifyGroupInvite(ChallengeSubmission submission, AppUser participant) {
        Challenge challenge = submission.getChallenge();
        notificationService.notifyUser(
                participant.getId(),
                submission.getUser().getId(),
                NotificationType.CHALLENGE_GROUP_INVITE_RECEIVED,
                "Invitation à un défi",
                displayName(submission.getUser()) + " t'a ajouté au défi \"" + challenge.getTitle() + "\".",
                Map.of(
                        "challengeId", challenge.getId().toString(),
                        "submissionId", submission.getId().toString(),
                        "submitterUserId", submission.getUser().getId().toString(),
                        "action", "CONFIRM_GROUP_CHALLENGE"
                ),
                "CHALLENGE_GROUP_INVITE",
                submission.getId()
        );
    }

    /**
     * Dire a quelqu'un qu'il ne fait plus partie du groupe.
     *
     * <p>Sans ce mot, le defi redevient disponible dans son application sans raison
     * visible : il a accepte, puis tout a disparu. Cela ressemble a une panne.</p>
     */
    private void notifyRemovedFromGroup(ChallengeSubmission submission, AppUser participant) {
        Challenge challenge = submission.getChallenge();
        notificationService.notifyUser(
                participant.getId(),
                submission.getUser().getId(),
                NotificationType.CHALLENGE_GROUP_MEMBER_REMOVED,
                "Retiré d'un groupe",
                displayName(submission.getUser()) + " t'a retiré du groupe pour le défi \""
                        + challenge.getTitle() + "\". Tu peux le refaire de ton côté.",
                Map.of(
                        "challengeId", challenge.getId().toString(),
                        "submissionId", submission.getId().toString()
                ),
                "CHALLENGE_GROUP_INVITE",
                submission.getId()
        );
    }

    @Transactional
    public void resendGroupSubmissionInvites(UUID submissionId, String keycloakSub) {
        AppUser player = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Joueur introuvable"));
        ChallengeSubmission submission = submissionRepository.findByIdForUpdate(submissionId)
                .orElseThrow(() -> new NotFoundException("Soumission introuvable"));

        ensureSubmissionSubmitter(submission, player);
        ensureWaitingGroupSubmission(submission);

        List<ChallengeSubmissionRepository.ParticipantConfirmationProjection> pendingParticipants =
                submissionRepository.findParticipantConfirmations(submissionId).stream()
                        .filter(participant -> "PENDING".equals(participant.getConfirmationStatus()))
                        .toList();
        if (pendingParticipants.isEmpty()) {
            throw new ConflictException("Aucune invitation n'est en attente.");
        }

        submissionRepository.touchPendingParticipantInvites(submissionId);
        notifyPendingGroupParticipants(submission, pendingParticipants);
    }

    /**
     * Pourquoi telle personne ne peut pas être invitée sur ce défi.
     *
     * Les mêmes règles que la soumission, posées au moment du choix : ne les
     * appliquer qu'à l'envoi obligeait à former l'équipe, prendre la photo, puis
     * découvrir le refus — sans savoir qui il visait.
     */
    @Transactional(readOnly = true)
    public List<ParticipantEligibilityDto> participantEligibility(String keycloakSub, UUID challengeId, List<UUID> userIds) {
        AppUser player = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Joueur non trouvé"));
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new NotFoundException("Défi non trouvé"));

        List<UUID> requested = userIds == null ? List.of() : userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (requested.isEmpty()) {
            return List.of();
        }

        Map<UUID, AppUser> usersById = userRepository.findAllById(requested).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));

        return requested.stream()
                .map(userId -> {
                    AppUser candidate = usersById.get(userId);
                    if (candidate == null) {
                        return new ParticipantEligibilityDto(userId, null, false, "Ce joueur est introuvable.");
                    }
                    String reason = participantBlocker(player, candidate, challenge);
                    return new ParticipantEligibilityDto(
                            userId, memberLabel(candidate), reason == null, reason);
                })
                .toList();
    }

    /**
     * La première raison qui bloque, ou {@code null}. L'ordre suit celui de la
     * soumission, pour que la phrase affichée à la sélection soit celle qu'on
     * recevrait à l'envoi.
     */
    private String participantBlocker(AppUser submitter, AppUser candidate, Challenge challenge) {
        if (candidate.getId().equals(submitter.getId())) {
            return null;
        }
        if (!departmentAllowed(candidate, challenge)) {
            String allowed = challenge.getTargetDepartments().stream()
                    .map(Department::getShortName)
                    .filter(Objects::nonNull)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.joining(", "));
            return allowed.isBlank()
                    ? "Pas dans un département autorisé."
                    : "Pas dans un département autorisé (" + allowed + ").";
        }
        List<String> missing = missingPrerequisites(candidate, challenge);
        if (!missing.isEmpty()) {
            return "N'a pas encore validé " + joinTitles(missing) + ".";
        }
        List<ChallengeSubmission> blocking = submissionRepository.findBlockingSubmissionsForUsersAndChallenge(
                List.of(candidate.getId()), challenge.getId());
        if (blocking.stream().anyMatch(submission -> STATUS_VALIDATED.equals(submission.getStatus()))) {
            return "A déjà validé ce défi.";
        }
        if (blocking.stream().anyMatch(submission -> STATUS_PENDING.equals(submission.getStatus()))) {
            return "A déjà une soumission en attente pour ce défi.";
        }
        if (blocking.stream().anyMatch(submission -> STATUS_WAITING_CONFIRMATION.equals(submission.getStatus()))) {
            return "A déjà une confirmation en attente pour ce défi.";
        }
        return null;
    }

    @Transactional
    public void submitChallenge(String keycloakSub, UUID challengeId, ChallengeSubmissionDto dto) {
        AppUser player = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Joueur non trouvé"));

        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new NotFoundException("Défi non trouvé"));

        if (challengeVisibility.hidden()) {
            throw new BadRequestException("Les défis sont momentanément indisponibles.");
        }

        if (VALIDATION_MODE_AUTO.equals(challenge.getValidationMode())) {
            throw new BadRequestException(
                    "Les défis automatiques ne peuvent pas être soumis par un joueur.",
                    "validationMode",
                    "La validation de ce défi est effectuée automatiquement."
            );
        }

        Set<AppUser> participants = resolveParticipants(player, dto.participantUserIds());
        validateMembersCanSubmit(player, participants, challenge);

        ChallengeSubmission submission = new ChallengeSubmission();
        submission.setUser(player);
        submission.setChallenge(challenge);
        submission.setParticipants(participants);

        // Le type de preuve est celui du défi, pas celui que le client annonce.
        // Le prendre au mot laissait soumettre une phrase là où le défi réclame
        // des fichiers — la soumission passait, sans aucune preuve à examiner.
        ProofType proofType = requireDeclaredProofType(challenge, dto.proofType());

        submission.setProofType(proofType.name());
        submission.setProofData(proofDataForSubmission(player, challenge, proofType, dto));
        submission.setComment(proofType == ProofType.MEDIA ? dto.comment() : null);
        // Declaree sensible par son auteur : la preuve sera purgee apres validation et
        // ne se regardera qu'entre personnes du meme genre, comme sur un defi sensible.
        // Un defi deja sensible l'est pour tout le monde, quoi qu'en dise l'envoi.
        submission.setSensitive(Boolean.TRUE.equals(dto.sensitive()));

        if (!participants.isEmpty()) {
            submission.setStatus(STATUS_WAITING_CONFIRMATION);
        } else {
            submission.setStatus(STATUS_PENDING);
        }

        submissionRepository.save(submission);
        Set<UUID> preAccepted = applyGroupInvitePreAcceptances(submission);
        if (ProofType.MEDIA.equals(proofType) && blankToNull(dto.comment()) != null) {
            ChallengeSubmissionMessage message = new ChallengeSubmissionMessage();
            message.setSubmission(submission);
            message.setAuthor(player);
            message.setAuthorKeycloakSub(player.getKeycloakSub());
            message.setAuthorDisplayName(displayName(player));
            message.setAuthorType(ChallengeSubmissionMessage.AUTHOR_PLAYER);
            message.setContent(dto.comment().trim());
            submissionMessageRepository.save(message);
        }
        if (STATUS_VALIDATED.equals(submission.getStatus())) {
            recalculateChallengePoints();
        }
        if (STATUS_WAITING_CONFIRMATION.equals(submission.getStatus())) {
            notifyGroupParticipants(submission, preAccepted);
        }
    }

    /**
     * Ceux qui ont rejoint en scannant ont deja dit oui.
     *
     * <p>Leur envoyer une invitation a confirmer ce qu'ils viennent de faire serait
     * exactement la redondance que le code QR existe pour supprimer. Si toute
     * l'equipe s'est formee ainsi, la preuve part sans attendre personne.</p>
     *
     * @return les identifiants de ceux qui n'ont donc rien a confirmer
     */
    private Set<UUID> applyGroupInvitePreAcceptances(ChallengeSubmission submission) {
        if (groupInviteRepository == null || submission.getParticipants().isEmpty()) {
            return Set.of();
        }

        Set<UUID> joined = groupInviteRepository
                .findByChallengeIdAndSubmitterId(submission.getChallenge().getId(), submission.getUser().getId())
                .map(ChallengeGroupInvite::getJoinedUserIds)
                .orElseGet(Set::of);
        if (joined.isEmpty()) {
            return Set.of();
        }

        submissionRepository.flush();
        Set<UUID> preAccepted = submission.getParticipants().stream()
                .map(AppUser::getId)
                .filter(joined::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        preAccepted.forEach(userId ->
                submissionRepository.updateParticipantConfirmation(submission.getId(), userId, "ACCEPTED"));

        promoteIfGroupReady(submission);
        submissionRepository.save(submission);
        return preAccepted;
    }

    @Transactional
    public void createApprovedManualSubmission(UUID userId, UUID challengeId, String comment, String actorKeycloakSub) {
        createApprovedManualSubmission(userId, challengeId, comment, null, actorKeycloakSub);
    }

    /**
     * Un defi accorde a la main, avec son ajustement.
     *
     * <p>Le bonus se posait jusqu'ici en deux temps : accorder, puis rouvrir la preuve
     * pour l'ajuster. Or celui qui accorde sait deja ce qu'il veut donner — et la
     * seconde etape, souvent oubliee, laissait la recompense nue.</p>
     *
     * <p>Le malus obeit a la meme regle qu'ailleurs : il ne descend sous zero que si le
     * defi l'autorise.</p>
     */
    @Transactional
    public void createApprovedManualSubmission(
            UUID userId, UUID challengeId, String comment, Integer bonusPoints, String actorKeycloakSub) {
        AppUser player = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Joueur introuvable"));
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new NotFoundException("Défi non trouvé"));

        validateMembersCanSubmit(player, Set.of(), challenge);

        ChallengeSubmission submission = new ChallengeSubmission();
        submission.setUser(player);
        submission.setChallenge(challenge);
        submission.setParticipants(Set.of());
        submission.setStatus(STATUS_VALIDATED);
        submission.setProofType(ProofType.TEXT.name());
        submission.setProofData("");
        submission.setComment(blankToNull(comment));
        int adjustment = bonusPoints == null ? 0 : bonusPoints;
        int baseReward = challenge.getPointsReward() == null ? 0 : challenge.getPointsReward();
        if (!Boolean.TRUE.equals(challenge.getAllowNegativeReward()) && (long) baseReward + adjustment < 0L) {
            throw new BadRequestException(
                    "Ajustement de points invalide.",
                    "bonusPoints",
                    "L'ajustement ne peut pas rendre la récompense finale négative. "
                            + "Autorise la perte de points sur ce défi pour pouvoir descendre sous zéro."
            );
        }
        submission.setBonusPoints(adjustment);
        submission.setValidatedAt(fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
        submissionRepository.save(submission);
        // Le commentaire est la seule chose qui dise pourquoi ce defi a ete
        // accorde sans preuve ; rien n'oblige a en laisser un, d'ou la cle
        // absente plutot que nulle quand il manque.
        Map<String, Object> grantDetails = new LinkedHashMap<>();
        if (submission.getId() != null) {
            grantDetails.put("submissionId", submission.getId().toString());
        }
        grantDetails.put("challengeId", challenge.getId().toString());
        grantDetails.put("challengeTitle", challenge.getTitle());
        grantDetails.put("points", challenge.getPointsReward());
        // Le registre porte l'ajustement des qu'il n'est pas nul : accorder cinquante
        // points de plus est une decision, et elle doit se relire.
        if (adjustment != 0) {
            grantDetails.put("bonusPoints", adjustment);
        }
        if (submission.getComment() != null) {
            grantDetails.put("comment", submission.getComment());
        }
        // Inerte aujourd'hui : setParticipants(Set.of()) plus haut fait de ce
        // chemin un geste solo par construction, donc putGroupComposition ne
        // trouve jamais de participants. La garde reste identique et ne coute
        // qu'une ligne ; elle devient active le jour ou ce chemin accepte un
        // groupe.
        putGroupComposition(grantDetails, submission);
        journal.record(actorKeycloakSub, player.getId(), AdminActionType.CHALLENGE_GRANTED, grantDetails);
        recalculateChallengePoints();
    }

    /** Une grâce ne se donne jamais pour plus d'une semaine : c'est un délai, pas une réouverture. */
    private static final int MAX_GRACE_HOURS = 168;
    private static final int DEFAULT_GRACE_HOURS = 24;

    /**
     * Rendre à un joueur le droit de reposter une preuve sur un défi fermé.
     *
     * <p>Un refus n'a jamais empêché de recommencer. Ce qui empêche, c'est la
     * fenêtre du défi : une preuve envoyée la veille de la fermeture et lue le
     * lendemain est refusée alors qu'il ne reste plus une minute pour la refaire.
     * Le joueur a fait le défi, il a mal cadré sa photo, et il n'a aucun
     * recours — c'est ce trou que la grâce comble.</p>
     *
     * <p>Elle va à tous les membres de la soumission, pas au seul auteur : le
     * refus les concernait tous, et un seul gracié ne pourrait de toute façon
     * pas reposter une preuve de groupe.</p>
     */
    @Transactional
    public void grantSubmissionGrace(
            UUID submissionId,
            String actorKeycloakSub,
            Integer hours,
            String reason
    ) {
        ChallengeSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Soumission introuvable"));

        // Sur une soumission encore en attente, la grâce ne veut rien dire : le
        // joueur n'a rien à refaire tant qu'un modérateur n'a pas tranché.
        if (!STATUS_REJECTED.equals(submission.getStatus())) {
            throw new BadRequestException("Une grâce ne s'accorde que sur une soumission refusée.");
        }

        int granted = hours == null ? DEFAULT_GRACE_HOURS : hours;
        if (granted < 1 || granted > MAX_GRACE_HOURS) {
            throw new BadRequestException("Le délai accordé doit tenir entre une heure et une semaine.");
        }

        Challenge challenge = submission.getChallenge();
        Instant now = TimeTravelContext.instantNow();
        Instant expiresAt = now.plus(Duration.ofHours(granted));

        AppUser actor = userRepository.findByKeycloakSub(actorKeycloakSub).orElse(null);

        LinkedHashSet<AppUser> members = new LinkedHashSet<>();
        members.add(submission.getUser());
        members.addAll(submission.getParticipants());

        for (AppUser member : members) {
            ChallengeGrace grace = new ChallengeGrace();
            grace.setChallenge(challenge);
            grace.setUser(member);
            grace.setSubmission(submission);
            grace.setGrantedBy(actor);
            grace.setReason(blankToNull(reason));
            grace.setExpiresAt(expiresAt);
            graceRepository.save(grace);

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("challengeId", challenge.getId().toString());
            details.put("challengeTitle", challenge.getTitle());
            details.put("submissionId", submission.getId().toString());
            details.put("hours", granted);
            details.put("expiresAt", expiresAt.toString());
            if (blankToNull(reason) != null) {
                details.put("reason", reason.trim());
            }
            journal.record(actorKeycloakSub, member.getId(), AdminActionType.CHALLENGE_GRACE_GRANTED, details);

            // Une grâce que personne n'annonce ne sert à rien : elle expire avant
            // que le joueur apprenne qu'il l'avait.
            notificationService.notifyUser(
                    member.getId(),
                    actor == null ? null : actor.getId(),
                    NotificationType.CHALLENGE_GRACE_GRANTED,
                    "Tu peux retenter ce défi",
                    graceMessage(challenge, granted, reason),
                    Map.of(
                            "challengeId", challenge.getId().toString(),
                            "submissionId", submission.getId().toString(),
                            "expiresAt", expiresAt.toString()
                    ),
                    "CHALLENGE_GRACE",
                    submission.getId()
            );
        }
    }

    private static String graceMessage(Challenge challenge, int hours, String reason) {
        String delay = hours == 24 ? "24 h" : hours + " h";
        String message = "« " + challenge.getTitle() + " » t'est rouvert pendant " + delay
                + " : tu peux renvoyer une preuve.";
        return blankToNull(reason) == null ? message : message + " " + reason.trim();
    }

    @Transactional
    public void revokeValidatedSubmission(
            UUID userId,
            UUID submissionId,
            String comment,
            String actorKeycloakSub
    ) {
        ChallengeSubmission submission = submissionRepository.findByIdForUpdate(submissionId)
                .orElseThrow(() -> new NotFoundException("Soumission introuvable"));

        boolean isMember = (submission.getUser() != null && userId.equals(submission.getUser().getId()))
                || (submission.getParticipants() != null && submission.getParticipants().stream().anyMatch(p -> userId.equals(p.getId())));
        if (!isMember) {
            throw new BadRequestException("La soumission n'appartient pas à cet utilisateur.");
        }

        if (!STATUS_VALIDATED.equals(submission.getStatus())) {
            throw new BadRequestException("Seule une soumission validée peut être refusée.");
        }

        AppUser moderator = userRepository.findByKeycloakSub(actorKeycloakSub).orElse(null);

        submission.setStatus(STATUS_REJECTED);
        String normalizedComment = blankToNull(comment);
        submission.setModerationComment(normalizedComment);
        submission.setBonusPoints(0);
        submission.setValidatedAt(null);

        if (normalizedComment != null) {
            ChallengeSubmissionMessage message = new ChallengeSubmissionMessage();
            message.setSubmission(submission);
            message.setAuthor(moderator);
            message.setAuthorKeycloakSub(actorKeycloakSub);
            message.setAuthorDisplayName(moderator == null ? "Administrateur" : displayName(moderator));
            message.setAuthorType(ChallengeSubmissionMessage.AUTHOR_MODERATOR);
            message.setContent(normalizedComment);
            submissionMessageRepository.save(message);
        }

        notifySubmissionMembers(submission, moderator, NotificationType.CHALLENGE_SUBMISSION_REJECTED);
        clearReviewLock(submission);
        purgeSensitiveProofMedia(submission);
        submissionRepository.save(submission);

        moderationAuditService.record(submissionId, actorKeycloakSub, "DECISION", "SUCCESS", Map.of("status", STATUS_REJECTED));

        recalculateChallengePoints();

        Map<String, Object> decisionDetails = new LinkedHashMap<>();
        decisionDetails.put("submissionId", submission.getId().toString());
        decisionDetails.put("challengeId", submission.getChallenge().getId().toString());
        decisionDetails.put("challengeTitle", submission.getChallenge().getTitle());
        decisionDetails.put("points", submission.getChallenge().getPointsReward());
        if (normalizedComment != null) {
            decisionDetails.put("comment", normalizedComment);
        }
        putGroupComposition(decisionDetails, submission);
        journal.record(
                actorKeycloakSub,
                submission.getUser().getId(),
                AdminActionType.CHALLENGE_REJECTED,
                decisionDetails
        );
    }

    @Transactional(readOnly = true)
    public ProofMediaStream openModerationProofMedia(UUID submissionId, int index, String keycloakSub,
                                                     Collection<String> authorities, String rangeHeader) {
        ChallengeSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Soumission introuvable"));
        AppUser moderator = moderatorForAccess(keycloakSub, authorities);
        if (!canModerateSubmission(moderator, authorities, submission)) {
            moderationAuditService.recordDenied(submissionId, keycloakSub, "media-permission");
            throw new ForbiddenException("Accès refusé : vous ne pouvez pas consulter cette preuve.");
        }
        if (!isReviewLockActive(submission, fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow()) || !reviewLockedBy(submission, keycloakSub)) {
            moderationAuditService.recordDenied(submissionId, keycloakSub, "media-lock-lost");
            throw new ConflictException("Vous devez prendre cette preuve en review avant de la consulter.");
        }
        if (ProofType.valueOf(submission.getProofType()) == ProofType.TEXT) {
            throw new NotFoundException("Média de preuve introuvable");
        }
        List<String> keys = proofStorageService.parseProofFiles(submission.getProofData());
        if (index < 0 || index >= keys.size()) throw new NotFoundException("Média de preuve introuvable");
        ProofMediaStream media = proofStorageService.openProofMedia(keys.get(index), rangeHeader);
        try {
            mediaReadAuditPublisher.publish(submissionId, keycloakSub, index, media.contentLength());
            return media;
        } catch (RuntimeException exception) {
            try { media.inputStream().close(); } catch (java.io.IOException ignored) { }
            throw exception;
        }
    }

    @Transactional
    public void respondToGroupSubmission(UUID submissionId, String keycloakSub, boolean accepted) {
        AppUser participant = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Participant introuvable"));

        ChallengeSubmission submission = submissionRepository.findByIdForUpdate(submissionId)
                .orElseThrow(() -> new NotFoundException("Soumission introuvable"));

        if (!STATUS_WAITING_CONFIRMATION.equals(submission.getStatus())) {
            throw new ConflictException("Cette invitation de groupe n'est plus en attente.");
        }

        if (submission.getUser().getId().equals(participant.getId())
                || submission.getParticipants().stream().noneMatch(user -> user.getId().equals(participant.getId()))) {
            throw new ForbiddenException("Accès refusé : vous n'êtes pas invité sur cette soumission.");
        }

        submissionRepository.updateParticipantConfirmation(
                submissionId,
                participant.getId(),
                accepted ? "ACCEPTED" : "DECLINED"
        );

        if (accepted) {
            notifyGroupInviteAnswer(submission, participant, NotificationType.CHALLENGE_GROUP_INVITE_ACCEPTED);
        } else {
            // Refuser, c'est se retirer -- pas annuler le travail des autres. La
            // soumission vivait jusqu'ici le temps d'un seul « non » : la preuve
            // etait perdue, les acceptations aussi, et le chef devait tout refaire.
            removeParticipantFrom(submission, participant);
            notifyGroupInviteAnswer(submission, participant, NotificationType.CHALLENGE_GROUP_INVITE_DECLINED);
        }

        promoteIfGroupReady(submission);
        submissionRepository.save(submission);
        if (STATUS_VALIDATED.equals(submission.getStatus())) {
            recalculateChallengePoints();
        }
        announceTeamChange(submission.getUser(), submission.getChallenge().getId(), submission.getId());
    }

    @Transactional(readOnly = true)
    public List<SubmissionMessageDto> getPlayerSubmissionMessages(UUID submissionId, String keycloakSub) {
        AppUser player = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Joueur introuvable"));
        ChallengeSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Soumission introuvable"));
        ensureSubmissionMember(submission, player);

        return submissionMessageRepository.findBySubmissionIdOrderByCreatedAtAsc(submissionId).stream()
                .map(message -> toPlayerSubmissionMessageDto(message, player))
                .toList();
    }

    @Transactional
    public SubmissionMessageDto createPlayerSubmissionMessage(
            UUID submissionId,
            String keycloakSub,
            SubmissionMessageCreateDto dto
    ) {
        AppUser player = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Joueur introuvable"));
        ChallengeSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Soumission introuvable"));
        ensureSubmissionMember(submission, player);
        ensureSubmissionCanReceiveMessages(submission);
        if (!Boolean.TRUE.equals(submission.getMessagesEnabled())) {
            throw new ForbiddenException("La discussion n'est pas ouverte aux joueurs.");
        }

        ChallengeSubmissionMessage message = createSubmissionMessage(
                submission,
                player,
                player.getKeycloakSub(),
                displayName(player),
                ChallengeSubmissionMessage.AUTHOR_PLAYER,
                dto
        );
        return toPlayerSubmissionMessageDto(submissionMessageRepository.save(message), player);
    }

    @Transactional(readOnly = true)
    public List<SubmissionMessageDto> getModerationSubmissionMessages(
            UUID submissionId,
            String keycloakSub,
            Collection<String> authorities
    ) {
        ChallengeSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Soumission introuvable"));
        AppUser moderator = moderatorForAccess(keycloakSub, authorities);
        if (!canModerateSubmission(moderator, authorities, submission)) {
            throw new ForbiddenException("Accès refusé : vous ne pouvez pas consulter cette discussion.");
        }

        return submissionMessageRepository.findBySubmissionIdOrderByCreatedAtAsc(submissionId).stream()
                .map(message -> toModerationSubmissionMessageDto(message, keycloakSub))
                .toList();
    }

    @Transactional
    public SubmissionMessageDto createModerationSubmissionMessage(
            UUID submissionId,
            String keycloakSub,
            Collection<String> authorities,
            SubmissionMessageCreateDto dto
    ) {
        ChallengeSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Soumission introuvable"));
        AppUser moderator = moderatorForMessage(keycloakSub, authorities);
        if (!canModerateSubmission(moderator, authorities, submission)) {
            throw new ForbiddenException("Accès refusé : vous ne pouvez pas écrire dans cette discussion.");
        }
        ensureSubmissionCanReceiveMessages(submission);

        ChallengeSubmissionMessage message = createSubmissionMessage(
                submission,
                moderator,
                keycloakSub,
                moderator == null ? "Administrateur" : displayName(moderator),
                ChallengeSubmissionMessage.AUTHOR_MODERATOR,
                dto
        );
        SubmissionMessageDto savedMessage = toModerationSubmissionMessageDto(submissionMessageRepository.save(message), keycloakSub);
        return savedMessage;
    }

    @Transactional
    public void updateSubmissionMessagesEnabled(
            UUID submissionId,
            String keycloakSub,
            Collection<String> authorities,
            Boolean enabled
    ) {
        ChallengeSubmission submission = submissionRepository.findByIdForUpdate(submissionId)
                .orElseThrow(() -> new NotFoundException("Soumission introuvable"));
        AppUser moderator = moderatorForAccess(keycloakSub, authorities);
        if (!canModerateSubmission(moderator, authorities, submission)) {
            throw new ForbiddenException("Accès refusé : vous ne pouvez pas modifier cette discussion.");
        }
        ensureSubmissionCanReceiveMessages(submission);
        submission.setMessagesEnabled(enabled);
        submissionRepository.save(submission);
    }

    /**
     * Creer un defi n'ecrit rien au registre : un defi neuf n'a aucune
     * soumission validee, donc aucun participant, et le recalcul qui suit ne
     * recredite personne. Ses points n'ont pas d'avant, ils n'ont qu'un apres.
     */
    @Transactional
    public AdminChallengeDto createChallenge(ChallengeCreateDto dto) {
        Challenge challenge = new Challenge();
        challenge.setTitle(dto.title());
        challenge.setDescription(dto.description());
        challenge.setPointsReward(dto.pointsReward());
        // Le partage suit le type : un objectif mesure sur l'equipe se partage entre
        // ses membres, sinon elle encaisserait autant de fois la mise qu'elle compte de
        // joueurs pour un seul accomplissement. Ce n'est donc pas un reglage
        // d'administration -- la case du panel ne fait que le refleter.
        challenge.setRewardShared(autoChallengeTypeRegistry.isTeamScoped(
                dto.autoChallengeType(), JsonPayloads.toNode(dto.autoChallengeParameters())));
        challenge.setStartDate(dto.startDate());
        challenge.setEndDate(dto.endDate());
        challenge.setPublishedAt(dto.publishedAt());
        challenge.setGlobalQuota(dto.globalQuota());
        challenge.setValidationMode(dto.validationMode());
        // Absent = MEDIA : un panneau qui ne connaît pas encore le champ garde le
        // comportement qu'avaient tous les défis avant qu'il existe.
        challenge.setProofType(dto.proofType() == null || dto.proofType().isBlank()
                ? ProofType.MEDIA.name()
                : dto.proofType());
        setAutomaticChallengeConfiguration(challenge, dto);
        challenge.setSensitive(dto.sensitive());
        challenge.setAllowNegativeReward(dto.allowNegativeReward());
        challenge.setPromo(dto.promo());
        challenge.setMinGroupSize(dto.minGroupSize());
        challenge.setMaxGroupSize(dto.maxGroupSize());
        validateChallengeConfig(challenge);
        validateGroupSizes(challenge);
        applyCategories(challenge, dto);
        validateDailyEndDate(challenge);
        setPrerequisiteChallenges(challenge, dto.prerequisiteChallengeIds(), null);

        // On lie les départements ciblés
        if (dto.targetDepartmentIds() != null && !dto.targetDepartmentIds().isEmpty()) {
            List<Department> targets = departmentRepository.findAllById(dto.targetDepartmentIds());
            validateAllDepartmentsFound(dto.targetDepartmentIds(), targets);
            challenge.setTargetDepartments(new java.util.HashSet<>(targets));
        }

        Challenge saved = challengeRepository.save(challenge);
        AdminChallengeDto savedChallenge = toAdminChallengeDto(saved);
        recalculateChallengePoints();
        publishAutoChallengeSaved(saved);
        return savedChallenge;
    }

    /**
     * Changer les points d'un defi recredite tous ses participants : le
     * recalcul qui suit repasse sur chaque soumission validee. La ligne du
     * registre nomme donc le defi et le nombre de joueurs credites, pas un
     * joueur - une ligne par participant en ferait des milliers pour un clic.
     *
     * <p>Un enregistrement qui laisse les points inchanges n'ecrit rien, meme
     * si le reste du defi a bouge : le registre parle de ce qui deplace des
     * points, pas d'un titre reecrit.</p>
     */
    @Transactional
    public AdminChallengeDto updateChallenge(UUID id, ChallengeCreateDto dto, String actorKeycloakSub) {
        Challenge challenge = challengeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Défi introuvable"));
        Integer previousPointsReward = challenge.getPointsReward();
        boolean becomesAutomatic = !VALIDATION_MODE_AUTO.equals(challenge.getValidationMode())
                && VALIDATION_MODE_AUTO.equals(dto.validationMode());

        // On met à jour les champs
        challenge.setTitle(dto.title());
        challenge.setDescription(dto.description());
        challenge.setPointsReward(dto.pointsReward());
        // Le partage suit le type : un objectif mesure sur l'equipe se partage entre
        // ses membres, sinon elle encaisserait autant de fois la mise qu'elle compte de
        // joueurs pour un seul accomplissement. Ce n'est donc pas un reglage
        // d'administration -- la case du panel ne fait que le refleter.
        challenge.setRewardShared(autoChallengeTypeRegistry.isTeamScoped(
                dto.autoChallengeType(), JsonPayloads.toNode(dto.autoChallengeParameters())));
        challenge.setStartDate(dto.startDate());
        challenge.setEndDate(dto.endDate());
        challenge.setPublishedAt(dto.publishedAt());
        challenge.setGlobalQuota(dto.globalQuota());
        challenge.setValidationMode(dto.validationMode());
        // Absent = MEDIA : un panneau qui ne connaît pas encore le champ garde le
        // comportement qu'avaient tous les défis avant qu'il existe.
        challenge.setProofType(dto.proofType() == null || dto.proofType().isBlank()
                ? ProofType.MEDIA.name()
                : dto.proofType());
        setAutomaticChallengeConfiguration(challenge, dto);
        challenge.setSensitive(dto.sensitive());
        challenge.setAllowNegativeReward(dto.allowNegativeReward());
        challenge.setPromo(dto.promo());
        challenge.setMinGroupSize(dto.minGroupSize());
        challenge.setMaxGroupSize(dto.maxGroupSize());
        validateChallengeConfig(challenge);
        validateGroupSizes(challenge);
        applyCategories(challenge, dto);
        validateDailyEndDate(challenge);
        setPrerequisiteChallenges(challenge, dto.prerequisiteChallengeIds(), id);

        // Mise à jour des départements ciblés
        challenge.getTargetDepartments().clear();
        if (dto.targetDepartmentIds() != null && !dto.targetDepartmentIds().isEmpty()) {
            List<Department> targets = departmentRepository.findAllById(dto.targetDepartmentIds());
            validateAllDepartmentsFound(dto.targetDepartmentIds(), targets);
            challenge.setTargetDepartments(new java.util.HashSet<>(targets));
        }

        Challenge saved = challengeRepository.save(challenge);
        if (becomesAutomatic) {
            retireQueueForAutomaticValidation(saved);
        }
        AdminChallengeDto savedChallenge = toAdminChallengeDto(saved);
        journalPointsChange(actorKeycloakSub, challenge, previousPointsReward);
        recalculateChallengePoints();
        // Une phrase modifiée vise parfois d'autres joueurs qu'avant : le seuil
        // baisse, la catégorie change. Eux aussi méritent d'être rattrapés.
        publishAutoChallengeSaved(saved);
        return savedChallenge;
    }

    /**
     * Vide la file d'un défi qui passe en automatique.
     *
     * <p>Une preuve en attente bloquerait l'attribution de son auteur tant que personne
     * ne la tranche, et une preuve de groupe confirmée après coup serait validée sans
     * modération. Retirées dans la même transaction, elles ne bloquent plus : le
     * rattrapage qui part après validation en base attribue le défi à qui remplit la
     * condition, y compris leurs auteurs.</p>
     *
     * <p>Ce n'est pas un refus de modérateur : rien au registre de modération, aucun
     * message en son nom. Le commentaire et la notification disent pourquoi.</p>
     */
    private void retireQueueForAutomaticValidation(Challenge challenge) {
        List<ChallengeSubmission> queue = submissionRepository.findByChallengeIdAndStatusIn(
                challenge.getId(), List.of(STATUS_PENDING, STATUS_WAITING_CONFIRMATION));
        for (ChallengeSubmission submission : queue) {
            submission.setStatus(STATUS_REJECTED);
            submission.setModerationComment(AUTOMATIC_SWITCH_COMMENT);
            submission.setValidatedAt(null);
            clearReviewLock(submission);
        }
        submissionRepository.saveAll(queue);
        for (ChallengeSubmission submission : queue) {
            notifySubmissionMembers(submission, null, NotificationType.CHALLENGE_SUBMISSION_REJECTED,
                    "Défi devenu automatique",
                    "\"" + challenge.getTitle() + "\" se valide désormais automatiquement : ta preuve n'est plus nécessaire.");
        }
    }

    @Transactional
    public ChallengeBulkUpdateResultDto bulkUpdateChallenges(ChallengeBulkUpdateDto dto, String actorKeycloakSub) {
        List<Challenge> challenges = challengeRepository.findAllById(dto.challengeIds());
        long uniqueRequestedCount = dto.challengeIds().stream().distinct().count();
        if (challenges.size() != uniqueRequestedCount) {
            throw new NotFoundException("Un ou plusieurs défis sont introuvables.");
        }

        List<String> resolvedCategories = null;
        if (dto.updateCategories()) {
            List<String> requested = dto.categories() == null || dto.categories().isEmpty()
                    ? List.of(ChallengeCategory.DEFAULT_NAME)
                    : dto.categories();
            resolvedCategories = requested.stream()
                    .filter(name -> name != null && !name.isBlank())
                    .map(this::resolveCategory)
                    .distinct()
                    .toList();
            if (resolvedCategories.isEmpty()) {
                resolvedCategories = List.of(ChallengeCategory.DEFAULT_NAME);
            }
        }

        for (Challenge challenge : challenges) {
            if (dto.updateStartDate()) {
                challenge.setStartDate(dto.startDate());
            }
            if (dto.updateEndDate()) {
                challenge.setEndDate(dto.endDate());
            }
            if (dto.updatePublishedAt()) {
                challenge.setPublishedAt(dto.publishedAt());
            }
            if (dto.updateCategories()) {
                challenge.setCategory(resolvedCategories.getFirst());
                challenge.setExtraCategories(new HashSet<>(resolvedCategories.subList(1, resolvedCategories.size())));
            }

            validateChallengeConfig(challenge);
            validateDailyEndDate(challenge);

            challengeRepository.save(challenge);
            publishAutoChallengeSaved(challenge);
        }

        // La purge vient apres les mises a jour : elle ne modifie pas le defi, elle
        // efface ce qui lui a ete envoye, et son echec ne doit pas emporter le reste.
        int purgedProofs = 0;
        if (Boolean.TRUE.equals(dto.purgeProofs())) {
            for (Challenge challenge : challenges) {
                purgedProofs += purgeChallengeProofs(challenge.getId(), actorKeycloakSub);
            }
        }

        return new ChallengeBulkUpdateResultDto(
                challenges.size(), dto.challengeIds().stream().distinct().toList(), purgedProofs);
    }

    private void journalPointsChange(String actorKeycloakSub, Challenge challenge, Integer previousPointsReward) {
        if (Objects.equals(previousPointsReward, challenge.getPointsReward())) {
            return;
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scope", "CHALLENGE");
        // Le defi vient de findById dans updateChallenge, seul appelant : son id
        // ne peut pas etre nul, et le comptage ci-dessous le passe deja tel quel.
        details.put("challengeId", challenge.getId().toString());
        details.put("challengeTitle", challenge.getTitle());
        details.put("fromPoints", previousPointsReward);
        details.put("toPoints", challenge.getPointsReward());
        // Sans ce nombre, "points du defi : 10 -> 30" ne dit pas si le geste a
        // touche trois joueurs ou trois cents. Un seul compte, en base.
        details.put("affectedPlayers", submissionRepository.countPlayersCreditedByChallenge(challenge.getId()));
        journal.recordGlobal(actorKeycloakSub, AdminActionType.CHALLENGE_POINTS_CHANGED, details);
    }

    /**
     * Supprimer un defi retire ses points a chacun de ses participants : le
     * recalcul qui suit recompte le score de chacun sans lui, et la
     * suppression en cascade des soumissions fait qu'il ne reste rien pour
     * reconstruire la ligne apres coup. C'est le seul geste du registre qui
     * detruit sa propre trace : d'ou une ligne, et d'ou son ecriture
     * <strong>avant</strong> la suppression.
     *
     * <p>Le comptage aussi doit preceder la suppression, pour la meme raison :
     * apres, les soumissions validees du defi n'existent plus et
     * {@code countPlayersCreditedByChallenge} repondrait zero sur un geste qui
     * a touche trois cents joueurs.</p>
     */
    @Transactional
    public void deleteChallenge(UUID id, String actorKeycloakSub) {
        Challenge challenge = challengeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Défi introuvable"));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scope", "CHALLENGE");
        details.put("challengeId", id.toString());
        details.put("challengeTitle", challenge.getTitle());
        details.put("points", challenge.getPointsReward());
        details.put("affectedPlayers", submissionRepository.countPlayersCreditedByChallenge(id));
        details.put("submissions", submissionRepository.countByChallengeId(id));
        journal.recordGlobal(actorKeycloakSub, AdminActionType.CHALLENGE_DELETED, details);

        // Marque, pas efface. Rien ne part : ni les preuves, ni les conversations, ni
        // les participations. Le defi sort des listes, et le recalcul qui suit retire
        // ses points parce qu'il ecarte desormais les defis supprimes -- les deux se
        // defont en remettant ce champ a NULL.
        challenge.setDeletedAt(Instant.now());
        challenge.setDeletedByKeycloakSub(actorKeycloakSub);
        challengeRepository.save(challenge);
        recalculateChallengePoints();
    }

    /**
     * Remettre un defi supprime, avec ses preuves et les points qu'elles donnaient.
     *
     * <p>Rien a reconstruire : la suppression n'avait rien efface. Le recalcul qui
     * suit rend leurs points a ceux qui avaient valide, puisque la requete cesse
     * d'ecarter ce defi.</p>
     */
    @Transactional
    public AdminChallengeDto restoreChallenge(UUID id, String actorKeycloakSub) {
        Challenge challenge = challengeRepository.findDeletedById(id)
                .orElseThrow(() -> new NotFoundException("Aucun défi supprimé ne porte cet identifiant."));

        challenge.setDeletedAt(null);
        challenge.setDeletedByKeycloakSub(null);
        Challenge restored = challengeRepository.save(challenge);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scope", "CHALLENGE");
        details.put("challengeId", id.toString());
        details.put("challengeTitle", restored.getTitle());
        details.put("points", restored.getPointsReward());
        // Compte apres la remise en place : c'est le nombre de joueurs qui retrouvent
        // leurs points, et il doit se lire en miroir de la ligne de suppression.
        details.put("affectedPlayers", submissionRepository.countPlayersCreditedByChallenge(id));
        details.put("submissions", submissionRepository.countByChallengeId(id));
        journal.recordGlobal(actorKeycloakSub, AdminActionType.CHALLENGE_RESTORED, details);

        recalculateChallengePoints();
        return toAdminChallengeDto(restored);
    }

    /**
     * La corbeille : ce qui a ete supprime et peut revenir.
     *
     * <p>Chaque ligne porte ce qu'une restauration rendrait -- les joueurs credites et
     * les preuves -- parce que c'est la seule question qu'on se pose devant cette
     * liste.</p>
     */
    @Transactional(readOnly = true)
    public List<DeletedChallengeDto> listDeletedChallenges() {
        List<Challenge> deleted = challengeRepository.findAllDeleted();
        Map<String, String> names = moderatorNames(deleted.stream()
                .map(Challenge::getDeletedByKeycloakSub)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList());
        return deleted.stream()
                .map(challenge -> new DeletedChallengeDto(
                        challenge.getId(),
                        challenge.getTitle(),
                        challenge.getPointsReward() == null ? 0 : challenge.getPointsReward(),
                        challenge.getDeletedAt(),
                        names.get(challenge.getDeletedByKeycloakSub()),
                        submissionRepository.countPlayersCreditedByChallenge(challenge.getId()),
                        submissionRepository.countByChallengeId(challenge.getId())))
                .toList();
    }

    /** Qui a supprime, par son nom affiche plutot que par son sub. */
    private Map<String, String> moderatorNames(List<String> keycloakSubs) {
        Map<String, String> names = new LinkedHashMap<>();
        for (String sub : keycloakSubs) {
            userRepository.findByKeycloakSub(sub).ifPresent(user -> names.put(sub, displayName(user)));
        }
        return names;
    }

    /**
     * Ce qu'une suppression emporterait, pour le dire avant de la faire.
     *
     * <p>La boite de dialogue disait « cette action est definitive » sans jamais
     * chiffrer ce qu'elle effacait. Trois cents joueurs credites, c'est la meme
     * phrase qu'un defi que personne n'a releve.</p>
     */
    @Transactional(readOnly = true)
    public ChallengeDeletionImpactDto deletionImpact(UUID id) {
        Challenge challenge = challengeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Défi introuvable"));
        return new ChallengeDeletionImpactDto(
                challenge.getId(),
                challenge.getTitle(),
                challenge.getPointsReward() == null ? 0 : challenge.getPointsReward(),
                submissionRepository.countPlayersCreditedByChallenge(id),
                submissionRepository.countByChallengeId(id));
    }

    @Transactional(readOnly = true)
    public List<PendingSubmissionDto> getPendingSubmissions(
            String keycloakSub,
            Collection<String> authorities
    ) {
        List<ChallengeSubmission> pendingSubmissions = submissionRepository.findByStatusOrderByCreatedAtAsc(STATUS_PENDING);

        if (hasRole(authorities, "ROLE_ADMIN")) {
            return pendingSubmissions.stream()
                    .map(submission -> toPendingSubmissionDto(submission, keycloakSub))
                    .collect(java.util.stream.Collectors.toList());
        }

        AppUser moderator = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Utilisateur non trouvé"));

        return pendingSubmissions.stream()
                .filter(submission -> canModerateSubmission(moderator, authorities, submission))
                .map(submission -> toPendingSubmissionDto(submission, keycloakSub))
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ModerationSubmissionDetailDto getModerationSubmissionDetail(
            UUID submissionId,
            String keycloakSub,
            Collection<String> authorities
    ) {
        ChallengeSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Soumission introuvable"));
        AppUser moderator = moderatorForAccess(keycloakSub, authorities);
        if (!canModerateSubmission(moderator, authorities, submission)) {
            throw new ForbiddenException("Accès refusé : vous ne pouvez pas consulter cette soumission.");
        }

        boolean reviewLocked = isReviewLockActive(submission, fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
        List<SubmissionMessageDto> messages = submissionMessageRepository
                .findBySubmissionIdOrderByCreatedAtAsc(submissionId).stream()
                .map(message -> toModerationSubmissionMessageDto(message, keycloakSub))
                .toList();

        return toModerationSubmissionDetailDto(submission, keycloakSub, reviewLocked, messages);
    }

    /** Ce que le défi attend comme preuve. Absent en base = MEDIA. */
    private static ProofType proofTypeOf(Challenge challenge) {
        String declared = challenge.getProofType();
        return declared == null || declared.isBlank() ? ProofType.MEDIA : ProofType.valueOf(declared);
    }

    /**
     * Le type de preuve du défi, et le refus quand la soumission en annonce un autre.
     *
     * <p>Un client à jour envoie ce que le défi demande ; un client plus ancien ne
     * connaît pas encore le champ et envoie MEDIA par défaut. Dans les deux cas,
     * c'est le défi qui décide, et l'écart se dit au lieu de se corriger en
     * silence : on n'attache pas des fichiers à une preuve texte.</p>
     */
    private static ProofType requireDeclaredProofType(Challenge challenge, String submitted) {
        ProofType expected = proofTypeOf(challenge);
        if (submitted == null || submitted.isBlank()) {
            return expected;
        }

        ProofType claimed;
        try {
            claimed = ProofType.valueOf(submitted);
        } catch (IllegalArgumentException unknown) {
            throw new BadRequestException("Type de preuve inconnu.");
        }

        if (claimed != expected) {
            throw new BadRequestException(expected == ProofType.TEXT
                    ? "Ce défi se valide par une preuve écrite."
                    : "Ce défi se valide par des fichiers.");
        }
        return expected;
    }

    private String proofDataForSubmission(
            AppUser player,
            Challenge challenge,
            ProofType proofType,
        ChallengeSubmissionDto dto
    ) {
        if (proofType == ProofType.TEXT) {
            if (dto.comment() == null || dto.comment().isBlank()) {
                throw new BadRequestException("La preuve texte est requise.");
            }

            return dto.comment();
        }

        String proofData = proofStorageService.attachSubmittedUploads(player, challenge, proofType, dto.proofUploadIds());
        proofStorageService.validateSubmittedProof(proofType, proofData);

        return proofData;
    }

    @Transactional
    public void lockSubmissionForReview(
            UUID submissionId,
            String keycloakSub,
            Collection<String> authorities
    ) {
        ChallengeSubmission submission = submissionRepository.findByIdForUpdate(submissionId)
                .orElseThrow(() -> new NotFoundException("Soumission introuvable"));

        AppUser moderator = hasRole(authorities, "ROLE_ADMIN")
                ? null
                : userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Modérateur introuvable"));

        if (!canModerateSubmission(moderator, authorities, submission)) {
            moderationAuditService.recordDenied(submissionId, keycloakSub, "lock-permission");
            throw new ForbiddenException("Accès refusé : vous ne pouvez pas prendre cette preuve en review.");
        }

        if (!STATUS_PENDING.equals(submission.getStatus())) {
            throw new ConflictException("Cette soumission a déjà été traitée.");
        }

        Instant now = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        if (isReviewLockActive(submission, now) && !reviewLockedBy(submission, keycloakSub)) {
            throw new ConflictException("Cette preuve est déjà en cours de modération par un autre membre du staff. "
                    + "Choisis une autre preuve ou réessaie lorsqu'elle sera libérée.");
        }

        submission.setReviewLockedBy(keycloakSub);
        submission.setReviewStartedAt(now);
        submission.setReviewExpiresAt(now.plus(REVIEW_LOCK_TTL));
        submissionRepository.save(submission);
        moderationAuditService.record(submissionId, keycloakSub, "LOCK_ACQUIRED", "SUCCESS", Map.of());
    }

    @Transactional
    public Instant heartbeatSubmissionReviewLock(
            UUID submissionId, String keycloakSub, Collection<String> authorities
    ) {
        ChallengeSubmission submission = submissionRepository.findByIdForUpdate(submissionId)
                .orElseThrow(() -> new NotFoundException("Soumission introuvable"));
        AppUser moderator = hasRole(authorities, "ROLE_ADMIN") ? null
                : userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Modérateur introuvable"));
        if (!canModerateSubmission(moderator, authorities, submission)) {
            moderationAuditService.recordDenied(submissionId, keycloakSub, "heartbeat-permission");
            throw new ForbiddenException("Accès refusé : vous ne pouvez pas renouveler cette review.");
        }
        Instant now = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        if (!STATUS_PENDING.equals(submission.getStatus())
                || !isReviewLockActive(submission, now)
                || !reviewLockedBy(submission, keycloakSub)) {
            moderationAuditService.recordDenied(submissionId, keycloakSub, "heartbeat-lock-lost");
            throw new ConflictException("Le verrou de review n'est plus actif.");
        }
        Instant maximum = submission.getReviewStartedAt().plus(REVIEW_LOCK_MAX_DURATION);
        if (!maximum.isAfter(now)) {
            throw new ConflictException("La durée maximale de review est atteinte.");
        }
        Instant expiresAt = now.plus(REVIEW_LOCK_TTL);
        if (expiresAt.isAfter(maximum)) expiresAt = maximum;
        submission.setReviewExpiresAt(expiresAt);
        submissionRepository.save(submission);
        moderationAuditService.record(submissionId, keycloakSub, "LOCK_RENEWED", "SUCCESS", Map.of());
        return expiresAt;
    }

    @Transactional
    public void releaseSubmissionReviewLock(
            UUID submissionId,
            String keycloakSub,
            Collection<String> authorities
    ) {
        ChallengeSubmission submission = submissionRepository.findByIdForUpdate(submissionId)
                .orElseThrow(() -> new NotFoundException("Soumission introuvable"));

        AppUser moderator = hasRole(authorities, "ROLE_ADMIN")
                ? null
                : userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Modérateur introuvable"));

        if (!canModerateSubmission(moderator, authorities, submission)) {
            moderationAuditService.recordDenied(submissionId, keycloakSub, "release-permission");
            throw new ForbiddenException("Accès refusé : vous ne pouvez pas libérer cette review.");
        }

        Instant now = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        if (isReviewLockActive(submission, now) && !reviewLockedBy(submission, keycloakSub)) {
            throw new ConflictException("Cette soumission est en review par un autre modérateur.");
        }

        clearReviewLock(submission);
        submissionRepository.save(submission);
        moderationAuditService.record(submissionId, keycloakSub, "LOCK_RELEASED", "SUCCESS", Map.of());
    }

    @Transactional
    public void decideSubmission(
            UUID submissionId,
            String keycloakSub,
            Collection<String> authorities,
            String newStatus,
            String moderationComment,
            Integer bonusPoints
    ) {
        ChallengeSubmission submission = submissionRepository.findByIdForUpdate(submissionId)
                .orElseThrow(() -> new NotFoundException("Soumission introuvable"));

        AppUser moderator = hasRole(authorities, "ROLE_ADMIN")
                ? null
                : userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Modérateur introuvable"));

        if (!canModerateSubmission(moderator, authorities, submission)) {
            moderationAuditService.recordDenied(submissionId, keycloakSub, "decision-permission");
            throw new ForbiddenException("Accès refusé : vous ne pouvez pas juger cette preuve.");
        }

        if (!STATUS_PENDING.equals(submission.getStatus())) {
            throw new ConflictException("Cette soumission a déjà été traitée.");
        }

        ensureReviewLockOwnedBy(submission, keycloakSub, "Vous devez prendre cette preuve en review avant de la juger.");
        String normalizedModerationComment = blankToNull(moderationComment);
        validatePointsAdjustmentForDecision(
                submission.getChallenge(), newStatus, normalizedModerationComment, bonusPoints);

        String previousStatus = submission.getStatus();
        submission.setStatus(newStatus);

        if (STATUS_VALIDATED.equals(newStatus)) {
            submission.setModerationComment(normalizedModerationComment);
            submission.setBonusPoints(bonusPoints);
            submission.setValidatedAt(fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
            notifySubmissionMembers(submission, moderator, NotificationType.CHALLENGE_SUBMISSION_VALIDATED);
        } else if (STATUS_REJECTED.equals(newStatus)) {
            submission.setModerationComment(normalizedModerationComment);
            submission.setBonusPoints(0);
            submission.setValidatedAt(null);
            if (normalizedModerationComment != null) {
                ChallengeSubmissionMessage message = new ChallengeSubmissionMessage();
                message.setSubmission(submission);
                message.setAuthor(moderator);
                message.setAuthorKeycloakSub(keycloakSub);
                message.setAuthorDisplayName(moderator == null ? "Administrateur" : displayName(moderator));
                message.setAuthorType(ChallengeSubmissionMessage.AUTHOR_MODERATOR);
                message.setContent(normalizedModerationComment);
                submissionMessageRepository.save(message);
            }
            notifySubmissionMembers(submission, moderator, NotificationType.CHALLENGE_SUBMISSION_REJECTED);
        }

        clearReviewLock(submission);
        purgeSensitiveProofMedia(submission);
        submissionRepository.save(submission);
        moderationAuditService.record(submissionId, keycloakSub, "DECISION", "SUCCESS", Map.of("status", newStatus));
        if (STATUS_VALIDATED.equals(newStatus) || STATUS_VALIDATED.equals(previousStatus)) {
            recalculateChallengePoints();
        }
        if (STATUS_VALIDATED.equals(newStatus)) {
            publishChallengeValidated(submission);
        }

        // Seules VALIDATED et REJECTED changent ce que le joueur possede : une
        // remise en attente n'entre pas dans le registre.
        if (STATUS_VALIDATED.equals(newStatus) || STATUS_REJECTED.equals(newStatus)) {
            Map<String, Object> decisionDetails = new LinkedHashMap<>();
            decisionDetails.put("submissionId", submission.getId().toString());
            decisionDetails.put("challengeId", submission.getChallenge().getId().toString());
            decisionDetails.put("challengeTitle", submission.getChallenge().getTitle());
            decisionDetails.put("points", submission.getChallenge().getPointsReward());
            if (STATUS_VALIDATED.equals(newStatus)) {
                // La part discretionnaire de la decision : c'est exactement le
                // "joueur soudain riche" que le registre existe pour expliquer.
                decisionDetails.put("bonusPoints", submission.getBonusPoints());
            }
            if (submission.getModerationComment() != null) {
                decisionDetails.put("comment", submission.getModerationComment());
            }
            putGroupComposition(decisionDetails, submission);
            journal.record(
                    keycloakSub,
                    submission.getUser().getId(),
                    STATUS_VALIDATED.equals(newStatus)
                            ? AdminActionType.CHALLENGE_VALIDATED
                            : AdminActionType.CHALLENGE_REJECTED,
                    decisionDetails
            );
        }
    }

    /**
     * Revoir les points bonus d'un defi deja valide.
     *
     * <p>La part discretionnaire de la decision ne se posait qu'au moment de la
     * validation. Accorder un bonus oublie demandait donc de refuser la preuve
     * pour la revalider : le joueur recevait « Defi refuse », sa preuve pouvait
     * etre effacee au passage, et il devait la renvoyer. Le geste existe
     * maintenant pour lui-meme.</p>
     *
     * <p>Le statut ne bouge pas : une preuve validee reste validee. Ce qui bouge,
     * c'est le nombre, et le motif qui l'explique -- au registre pour
     * l'encadrement, et au joueur, qui verrait sinon son score changer sans
     * raison.</p>
     */
    @Transactional
    public void adjustValidatedSubmissionBonusPoints(
            UUID submissionId,
            String keycloakSub,
            Collection<String> authorities,
            Integer bonusPoints,
            String reason
    ) {
        ChallengeSubmission submission = submissionRepository.findByIdForUpdate(submissionId)
                .orElseThrow(() -> new NotFoundException("Soumission introuvable"));

        AppUser moderator = moderatorForAccess(keycloakSub, authorities);
        if (!canModerateSubmission(moderator, authorities, submission)) {
            moderationAuditService.recordDenied(submissionId, keycloakSub, "bonus-adjustment-permission");
            throw new ForbiddenException("Accès refusé : vous ne pouvez pas ajuster les points de cette preuve.");
        }

        if (!STATUS_VALIDATED.equals(submission.getStatus())) {
            throw new ConflictException("Seule une soumission validée peut voir ses points ajustés.");
        }

        String normalizedReason = blankToNull(reason);
        if (normalizedReason == null) {
            throw new BadRequestException(
                    "Motif requis.",
                    "reason",
                    "Un motif est requis : c'est lui qui explique le changement au joueur et au registre."
            );
        }
        if (normalizedReason.length() > 1000) {
            throw new BadRequestException(
                    "Motif trop long.",
                    "reason",
                    "Le motif ne doit pas dépasser 1000 caractères."
            );
        }

        int adjusted = bonusPoints == null ? 0 : bonusPoints;
        int baseReward = submission.getChallenge().getPointsReward() == null
                ? 0
                : submission.getChallenge().getPointsReward();
        // Un malus ne mord sous zero que si le defi l'autorise : ailleurs, un chiffre
        // mal saisi retirerait des points a qui n'a rien demande.
        boolean allowsLoss = Boolean.TRUE.equals(submission.getChallenge().getAllowNegativeReward());
        if (!allowsLoss && (long) baseReward + adjusted < 0L) {
            throw new BadRequestException(
                    "Ajustement de points invalide.",
                    "bonusPoints",
                    "L'ajustement ne peut pas rendre la récompense finale négative. "
                            + "Autorise la perte de points sur ce défi pour pouvoir descendre sous zéro."
            );
        }

        int previousBonus = submission.getBonusPoints() == null ? 0 : submission.getBonusPoints();
        if (previousBonus == adjusted) {
            throw new BadRequestException(
                    "Aucun changement.",
                    "bonusPoints",
                    "Cette preuve porte déjà ce nombre de points bonus."
            );
        }

        submission.setBonusPoints(adjusted);
        submissionRepository.save(submission);

        moderationAuditService.record(submissionId, keycloakSub, "BONUS_ADJUSTED", "SUCCESS",
                Map.of("bonusPoints", adjusted, "previousBonusPoints", previousBonus));

        notifyBonusAdjustment(submission, moderator, previousBonus, adjusted, normalizedReason);

        // Le classement se lit sur la somme des recompenses : sans recalcul, le
        // nombre change en base et nulle part ailleurs.
        recalculateChallengePoints();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("submissionId", submission.getId().toString());
        details.put("challengeId", submission.getChallenge().getId().toString());
        details.put("challengeTitle", submission.getChallenge().getTitle());
        details.put("points", submission.getChallenge().getPointsReward());
        details.put("bonusPoints", adjusted);
        details.put("previousBonusPoints", previousBonus);
        details.put("comment", normalizedReason);
        putGroupComposition(details, submission);
        journal.record(
                keycloakSub,
                submission.getUser().getId(),
                AdminActionType.CHALLENGE_BONUS_ADJUSTED,
                details
        );
    }

    /**
     * Dire au joueur ce qui a change, et pourquoi.
     *
     * <p>Un score qui bouge sans explication se lit comme un bug, et sur une
     * baisse comme une sanction muette. Le motif ecrit par l'encadrement voyage
     * donc jusqu'a lui : il est ecrit pour etre lu.</p>
     */
    private void notifyBonusAdjustment(
            ChallengeSubmission submission,
            AppUser moderator,
            int previousBonus,
            int adjusted,
            String reason
    ) {
        Challenge challenge = submission.getChallenge();
        Map<String, Object> payload = Map.of(
                "challengeId", challenge.getId().toString(),
                "submissionId", submission.getId().toString(),
                "status", submission.getStatus(),
                "pointsReward", challenge.getPointsReward(),
                "bonusPoints", adjusted,
                "previousBonusPoints", previousBonus
        );
        String title = adjusted > previousBonus ? "Points bonus accordés" : "Points ajustés";
        String body = "Les points de ta preuve pour \"" + challenge.getTitle() + "\" ont changé : "
                + signed(previousBonus) + " → " + signed(adjusted) + ". " + reason;

        for (AppUser member : submissionMembers(submission)) {
            notificationService.notifyUser(
                    member.getId(),
                    moderator == null ? null : moderator.getId(),
                    NotificationType.CHALLENGE_BONUS_ADJUSTED,
                    title,
                    body,
                    payload,
                    "CHALLENGE_SUBMISSION",
                    submission.getId()
            );
        }
    }

    /** « +30 » plutot que « 30 » : le signe est l'information. */
    private static String signed(int points) {
        return points > 0 ? "+" + points : String.valueOf(points);
    }

    @Transactional(readOnly = true)
    public List<ModerationParticipantCandidateDto> searchModerationParticipantCandidates(
            UUID submissionId,
            String query,
            String keycloakSub,
            Collection<String> authorities
    ) {
        ChallengeSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Soumission introuvable"));
        AppUser moderator = moderatorForAccess(keycloakSub, authorities);
        if (!canModerateSubmission(moderator, authorities, submission)) {
            throw new ForbiddenException("Accès refusé : vous ne pouvez pas modifier ce groupe.");
        }
        ensureReviewLockOwnedBy(submission, keycloakSub,
                "Vous devez prendre cette preuve en review avant de modifier ses participants.");

        String normalizedQuery = blankToNull(query);
        if (normalizedQuery == null || normalizedQuery.length() < 2) {
            return List.of();
        }

        Set<UUID> currentMemberIds = submissionMembers(submission).stream()
                .map(AppUser::getId)
                .collect(Collectors.toSet());
        return userRepository.searchAdminUsers(normalizedQuery, null, null, null, null,
                        Set.of(), null, false, null, PageRequest.of(0, 20))
                .stream()
                .filter(candidate -> !currentMemberIds.contains(candidate.getId()))
                .map(candidate -> {
                    String reason = participantBlocker(
                            submission.getUser(), candidate, submission.getChallenge());
                    return new ModerationParticipantCandidateDto(
                            candidate.getId(), memberLabel(candidate), candidate.getEmail(),
                            reason == null, reason);
                })
                .toList();
    }

    @Transactional
    public SubmissionParticipantDto addModerationParticipant(
            UUID submissionId,
            UUID participantId,
            String keycloakSub,
            Collection<String> authorities
    ) {
        ChallengeSubmission submission = submissionRepository.findByIdForUpdate(submissionId)
                .orElseThrow(() -> new NotFoundException("Soumission introuvable"));
        AppUser moderator = moderatorForAccess(keycloakSub, authorities);
        if (!canModerateSubmission(moderator, authorities, submission)) {
            throw new ForbiddenException("Accès refusé : vous ne pouvez pas modifier ce groupe.");
        }
        if (!STATUS_PENDING.equals(submission.getStatus())) {
            throw new ConflictException("Les participants ne peuvent être modifiés qu'avant la décision.");
        }
        ensureReviewLockOwnedBy(submission, keycloakSub,
                "Vous devez prendre cette preuve en review avant de modifier ses participants.");

        if (submissionMembers(submission).stream().anyMatch(member -> member.getId().equals(participantId))) {
            throw new ConflictException("Ce joueur participe déjà à cette soumission.");
        }
        AppUser participant = userRepository.findById(participantId)
                .orElseThrow(() -> new NotFoundException("Joueur introuvable"));
        String blocker = participantBlocker(submission.getUser(), participant, submission.getChallenge());
        if (blocker != null) {
            throw new BadRequestException(blocker, "userId", blocker);
        }

        Set<AppUser> participants = new LinkedHashSet<>(submission.getParticipants());
        participants.add(participant);
        validateGroupSize(participants, submission.getChallenge());
        submission.setParticipants(participants);
        submissionRepository.saveAndFlush(submission);
        if (submissionRepository.updateParticipantConfirmation(
                submissionId, participantId, "ACCEPTED") != 1) {
            throw new IllegalStateException("La confirmation du participant ajouté a échoué.");
        }

        Instant now = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        moderationAuditService.record(submissionId, keycloakSub, "PARTICIPANT_ADDED", "SUCCESS",
                Map.of("participantId", participantId.toString()));
        return new SubmissionParticipantDto(
                participantId, memberLabel(participant), participant.getEmail(),
                "ACCEPTED", now, now, false, false);
    }

    /**
     * Efface les fichiers d'une preuve sensible, l'arbitrage rendu.
     *
     * <p>Un defi sensible recoit des photos que personne n'a de raison de garder une fois
     * la decision prise. La seule duree de conservation qui se defend est celle de
     * l'examen : elle s'arrete ici.</p>
     *
     * <p>Les cles sont videes en meme temps, sinon les ecrans continueraient de demander
     * des fichiers qui n'existent plus. La date de purge prend leur place — sans elle, une
     * preuve effacee ressemblerait a une preuve qui n'a jamais eu de fichier.</p>
     *
     * <p>Une suppression qui echoue ne fait pas echouer la decision : l'arbitrage est
     * rendu, et le rejouer n'est pas possible. Le fichier restant est signale dans les
     * logs.</p>
     */
    private void purgeSensitiveProofMedia(ChallengeSubmission submission) {
        Challenge challenge = submission.getChallenge();
        if (challenge == null) {
            return;
        }
        // Deux raisons d'effacer, et une seule mecanique : le defi sensible, dont
        // les photos ne se gardent que le temps de l'examen, et le joueur qui a
        // demande que les siennes partent une fois le defi valide.
        // Trois raisons d'effacer, une seule mecanique : le defi que l'organisation
        // declare sensible, la preuve que son auteur declare telle, et le joueur qui a
        // demande que les siennes partent une fois le defi valide.
        boolean sensitive = Boolean.TRUE.equals(challenge.getSensitive())
                || Boolean.TRUE.equals(submission.getSensitive());
        if (!sensitive && !askedForPurgeOnValidation(submission)) {
            return;
        }
        if (submission.getProofMediaPurgedAt() != null) {
            return;
        }
        List<String> objectKeys = proofStorageService.parseProofFiles(submission.getProofData());
        if (objectKeys.isEmpty()) {
            return;
        }
        proofStorageService.purgeSubmittedProof(submission.getProofData());
        submission.setProofData("[]");
        submission.setProofMediaPurgedAt(fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
    }

    /**
     * Efface les preuves deja tranchees d'un defi.
     *
     * <p>Les fichiers partent du stockage, definitivement : c'est la meme mecanique
     * que pour un defi sensible, dont les photos ne se gardent que le temps de
     * l'examen. Ce qui reste, c'est la decision — statut, points, commentaire — et
     * le registre de qui a efface.</p>
     *
     * <p>Les preuves en attente sont epargnees. Sans sa photo, une soumission devient
     * injugeable et bloquerait la file de moderation sans qu'on sache pourquoi.</p>
     *
     * @return le nombre de preuves dont les fichiers sont partis
     */
    @Transactional
    public int purgeChallengeProofs(UUID challengeId, String actorKeycloakSub) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new NotFoundException("Défi non trouvé"));

        int purged = 0;
        List<ChallengeSubmission> decided = submissionRepository.findByChallengeIdAndStatusIn(
                challengeId, List.of(STATUS_VALIDATED, STATUS_REJECTED));
        for (ChallengeSubmission submission : decided) {
            if (submission.getProofMediaPurgedAt() != null) {
                continue;
            }
            if (proofStorageService.parseProofFiles(submission.getProofData()).isEmpty()) {
                continue;
            }
            proofStorageService.purgeSubmittedProof(submission.getProofData());
            submission.setProofData("[]");
            submission.setProofMediaPurgedAt(fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
            purged++;
        }

        if (purged > 0) {
            submissionRepository.saveAll(decided);
        }
        // « purgedMedia » et non « proofsPurged » : le registre refuse toute cle
        // contenant l'un des cinq fragments de AdminActionJournal -- « proof » en fait
        // partie, pour qu'aucune URL signee n'y atterrisse. La cle refusee faisait
        // echouer l'effacement apres la suppression des fichiers, que l'annulation de
        // la transaction ne ramenait pas.
        journal.recordGlobal(actorKeycloakSub, AdminActionType.CHALLENGE_PROOFS_PURGED, Map.of(
                "challengeId", challenge.getId().toString(),
                "challengeTitle", challenge.getTitle(),
                "purgedMedia", purged,
                "submissionsExamined", decided.size()
        ));
        return purged;
    }

    /**
     * Un membre de cette soumission validee a-t-il demande l'effacement ?
     *
     * <p>Sur une preuve de groupe, la demande d'un seul suffit : la photo montre
     * tout le monde, et le refus de la laisser trainer ne se partage pas en
     * parts. Elle a servi — le defi est valide —, elle peut partir.</p>
     *
     * <p>Rien n'est efface avant la validation : une preuve refusee peut encore
     * etre discutee, et une preuve en attente n'a pas ete regardee.</p>
     */
    private boolean askedForPurgeOnValidation(ChallengeSubmission submission) {
        if (!STATUS_VALIDATED.equals(submission.getStatus())) {
            return false;
        }
        if (submission.getUser() != null && submission.getUser().isPurgeProofsAfterValidation()) {
            return true;
        }
        return submission.getParticipants().stream().anyMatch(AppUser::isPurgeProofsAfterValidation);
    }

    /** A group submission validates for everyone on it, so every member is re-evaluated. */
    private void publishChallengeValidated(ChallengeSubmission submission) {
        autoChallengeTriggerPublisher.publish(submission.getUser().getId(), AutoChallengeTrigger.CHALLENGE_VALIDATED);
        submission.getParticipants().forEach(participant ->
                autoChallengeTriggerPublisher.publish(participant.getId(), AutoChallengeTrigger.CHALLENGE_VALIDATED));
    }

    private void notifySubmissionMembers(
            ChallengeSubmission submission,
            AppUser moderator,
            NotificationType type
    ) {
        Challenge challenge = submission.getChallenge();
        String title = type == NotificationType.CHALLENGE_SUBMISSION_VALIDATED
                ? "Défi validé"
                : "Défi refusé";
        String body = type == NotificationType.CHALLENGE_SUBMISSION_VALIDATED
                ? "Ta preuve pour \"" + challenge.getTitle() + "\" a été validée."
                : "Ta preuve pour \"" + challenge.getTitle() + "\" a été refusée.";
        notifySubmissionMembers(submission, moderator, type, title, body);
    }

    private void notifySubmissionMembers(
            ChallengeSubmission submission,
            AppUser moderator,
            NotificationType type,
            String title,
            String body
    ) {
        Challenge challenge = submission.getChallenge();
        Map<String, Object> payload = Map.of(
                "challengeId", challenge.getId().toString(),
                "submissionId", submission.getId().toString(),
                "status", submission.getStatus(),
                "pointsReward", challenge.getPointsReward(),
                "bonusPoints", submission.getBonusPoints()
        );

        for (AppUser member : submissionMembers(submission)) {
            notificationService.notifyUser(
                    member.getId(),
                    moderator == null ? null : moderator.getId(),
                    type,
                    title,
                    body,
                    payload,
                    "CHALLENGE_SUBMISSION",
                    submission.getId()
            );
        }
    }

    /**
     * Ce qu'on met en avant, et pourquoi.
     *
     * <p>Trois raisons qui ne se remplacent pas : ce qui ferme bientôt (l'urgence
     * évite qu'un défi expire inaperçu), ce que des amis ont fait (l'argument
     * social, qui pousse aussi les défis de groupe), et les catégories où le
     * joueur réussit déjà (la suite de ce qu'il aime faire).
     *
     * <p>Un même défi n'apparaît qu'une fois : la première raison qui le nomme le
     * garde. Une liste qui répète le même titre sous trois étiquettes donne
     * l'impression qu'il n'y a rien d'autre à proposer.
     */
    @Transactional(readOnly = true)
    public List<ChallengeRecommendationDto> getRecommendations(String keycloakSub, int limit) {
        AppUser player = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Joueur non trouvé en base"));

        // Les quotidiens ont deja leur place, en haut de la page et en grand : les
        // remettre ici les compte deux fois. Ils ferment tous dans les vingt-quatre
        // heures, donc « se termine bientot » les remonterait systematiquement et
        // noierait les autres raisons.
        List<PlayerChallengeDto> open = visiblePlayerChallenges(player).stream()
                .filter(PlayerChallengeDto::canStillComplete)
                .filter(challenge -> !isDaily(challenge))
                .toList();
        if (open.isEmpty()) {
            return List.of();
        }

        Map<UUID, PlayerChallengeDto> openById = open.stream()
                .collect(Collectors.toMap(PlayerChallengeDto::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        LinkedHashMap<UUID, ChallengeRecommendationDto> picked = new LinkedHashMap<>();
        int wanted = Math.max(1, Math.min(limit, 12));

        // Les défis automatiques déjà entamés passent devant : le joueur a fourni
        // l'effort, il ne sait simplement pas qu'il en reste peu. Un compteur à
        // zéro ne dit rien de plus qu'un défi non commencé, donc on l'écarte.
        almostDoneRecommendations(player, open, wanted)
                .forEach(recommendation -> picked.putIfAbsent(recommendation.challenge().id(), recommendation));

        Instant now = TimeTravelContext.instantNow();
        Instant soon = now.plus(Duration.ofDays(7));
        open.stream()
                .filter(challenge -> challenge.endDate() != null && challenge.endDate().isBefore(soon))
                .sorted(Comparator.comparing(PlayerChallengeDto::endDate))
                .limit(wanted)
                .forEach(challenge -> picked.putIfAbsent(challenge.id(), ChallengeRecommendationDto.of(
                        challenge, ChallengeRecommendationReason.ENDING_SOON, endingSoonDetail(now, challenge.endDate())
                )));

        List<UUID> friendIds = friendshipRepository.findAcceptedFriendIds(player.getId());
        if (!friendIds.isEmpty()) {
            List<UUID> alreadyDone = submissionRepository.findValidatedChallengeIdsByUserIdOrParticipantUserId(player.getId());
            // `not in` sur une liste vide casse la requête : on lui donne toujours
            // au moins un identifiant, celui qu'aucun défi ne porte.
            List<UUID> excluded = alreadyDone.isEmpty() ? List.of(new UUID(0L, 0L)) : alreadyDone;
            submissionRepository.findChallengeIdsValidatedByFriends(friendIds, excluded, PageRequest.of(0, wanted * 3))
                    .stream()
                    .map(openById::get)
                    .filter(Objects::nonNull)
                    .limit(wanted)
                    .forEach(challenge -> picked.putIfAbsent(challenge.id(), ChallengeRecommendationDto.of(
                            challenge, ChallengeRecommendationReason.DONE_BY_FRIENDS, "Des amis l'ont déjà fait"
                    )));
        }

        Set<String> strongCategories = strongCategories(player);
        open.stream()
                .filter(challenge -> strongCategories.contains(challenge.category()))
                .sorted(Comparator.comparing(
                        (PlayerChallengeDto challenge) -> challenge.pointsReward() == null ? 0 : challenge.pointsReward()
                ).reversed())
                .limit(wanted)
                .forEach(challenge -> picked.putIfAbsent(challenge.id(), ChallengeRecommendationDto.of(
                        challenge, ChallengeRecommendationReason.STRONG_CATEGORY, "Dans « " + challenge.category() + " », que tu réussis"
                )));

        return picked.values().stream().limit(wanted).toList();
    }

    /** Un quotidien l'est autant s'il porte DAILY en catégorie secondaire. */
    private static boolean isDaily(PlayerChallengeDto challenge) {
        if (ChallengeCategory.DAILY_NAME.equals(challenge.category())) {
            return true;
        }
        return challenge.categories() != null && challenge.categories().contains(ChallengeCategory.DAILY_NAME);
    }

    /**
     * Les défis automatiques entamés mais pas finis, le plus avancé d'abord.
     *
     * <p>On ne lit les compteurs que des défis automatiques encore ouverts, et
     * seulement au moment d'établir la liste : c'est quelques lectures, pas une
     * par défi de la page.</p>
     */
    private List<ChallengeRecommendationDto> almostDoneRecommendations(
            AppUser player, List<PlayerChallengeDto> open, int wanted) {
        List<PlayerChallengeDto> automatic = open.stream()
                .filter(challenge -> VALIDATION_MODE_AUTO.equals(challenge.validationMode()))
                .toList();
        if (automatic.isEmpty()) {
            return List.of();
        }

        Map<UUID, Challenge> entities = challengeRepository
                .findAllById(automatic.stream().map(PlayerChallengeDto::id).toList())
                .stream()
                .collect(Collectors.toMap(Challenge::getId, Function.identity(), (a, b) -> a));

        record Started(PlayerChallengeDto challenge, AutoChallengeProgress progress) {}
        return automatic.stream()
                .map(challenge -> new Started(challenge, progressOrNull(entities.get(challenge.id()), player.getId())))
                .filter(started -> started.progress() != null)
                // Zéro n'est pas un début, et un compteur plein n'attend plus rien
                // du joueur : ni l'un ni l'autre ne mérite d'être mis en avant.
                .filter(started -> started.progress().current() > 0 && !started.progress().completed())
                .sorted(Comparator.comparingInt((Started started) -> started.progress().percentage()).reversed())
                .limit(wanted)
                .map(started -> new ChallengeRecommendationDto(
                        started.challenge(),
                        ChallengeRecommendationReason.ALMOST_DONE,
                        almostDoneDetail(started.progress()),
                        started.progress().percentage()))
                .toList();
    }

    /** L'avancement d'un défi automatique, ou {@code null} si on ne peut pas le lire. */
    private AutoChallengeProgress progressOrNull(Challenge challenge, UUID userId) {
        if (challenge == null || challenge.getAutoChallengeType() == null) {
            return null;
        }
        try {
            return autoChallengeTypeRegistry.progress(
                    challenge.getAutoChallengeType(), challenge.getAutoChallengeParameters(), userId);
        } catch (RuntimeException unreadable) {
            // Une phrase mal configurée retire le défi de la liste, elle ne casse
            // pas la page — comme pour l'avancement affiché sur la carte.
            return null;
        }
    }

    private static String almostDoneDetail(AutoChallengeProgress progress) {
        long remaining = progress.target() - progress.current();
        String left = remaining <= 1 ? "plus qu'un" : "plus que " + remaining;
        return "Déjà " + progress.current() + " / " + progress.target() + " — " + left;
    }

    /** Les catégories où le joueur a déjà validé, la plus fournie d'abord. */
    private Set<String> strongCategories(AppUser player) {
        List<UUID> validatedIds = submissionRepository.findValidatedChallengeIdsByUserIdOrParticipantUserId(player.getId());
        if (validatedIds.isEmpty()) {
            return Set.of();
        }
        Map<String, Long> byCategory = challengeRepository.findAllById(validatedIds).stream()
                .map(Challenge::getCategory)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return byCategory.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String endingSoonDetail(Instant now, Instant endDate) {
        long hours = Duration.between(now, endDate).toHours();
        if (hours <= 0) {
            return "Se termine dans l'heure";
        }
        if (hours < 24) {
            return "Se termine dans " + hours + " h";
        }
        long days = Duration.between(now, endDate).toDays();
        return days <= 1 ? "Se termine demain" : "Se termine dans " + days + " jours";
    }

    private List<PlayerChallengeDto> visiblePlayerChallenges(AppUser player) {
        // Le rideau global passe avant tout le reste : masqués, les défis ne sont
        // pas seulement invisibles, ils sont hors de portée — voir plus bas, la
        // soumission les refuse aussi.
        if (challengeVisibility.hidden()) {
            return List.of();
        }

        // Le departement force par le voyage dans le temps passe par ici comme
        // ailleurs : lire player.getDepartment() directement l'ignorerait sans
        // rien dire, ce qui est precisement ce que faisait la page des events.
        UUID departmentId = TimeTravelContext.departmentIdOr(player.getDepartment());
        if (departmentId == null) {
            return List.of();
        }

        Set<UUID> favoriteChallengeIds = favoriteRepository.findByUserId(player.getId()).stream()
                .map(favorite -> favorite.getChallenge().getId())
                .collect(Collectors.toSet());

        // Les deux lectures qui rendaient la page lineaire en nombre de defis, payees
        // une fois pour toute la page. A quatre-vingts defis, elles couvraient a elles
        // seules cent soixante requetes par ouverture, et par joueur.
        Set<UUID> validatedChallengeIds =
                Set.copyOf(submissionRepository.validatedChallengeIdsForUser(player.getId()));
        Map<UUID, List<ChallengeSubmission>> submissionsByChallenge =
                submissionRepository.findByUserIdOrParticipantUserId(player.getId()).stream()
                        .collect(Collectors.groupingBy(submission -> submission.getChallenge().getId()));
        Map<UUID, Instant> graces = activeGracesByChallenge(player);
        Map<UUID, PromoState> promoStates = promoStatesFor(departmentId);

        return challengeRepository.findChallengesForDepartment(departmentId).stream()
                .filter(this::published)
                .filter(challenge -> prerequisitesCompleted(challenge, validatedChallengeIds))
                .map(challenge -> toPlayerChallengeDto(player, challenge,
                        favoriteChallengeIds.contains(challenge.getId()),
                        submissionsByChallenge.getOrDefault(challenge.getId(), List.of()),
                        graces,
                        promoStates))
                .toList();
    }

    private PlayerChallengeDto toPlayerChallengeDto(AppUser player, Challenge challenge, boolean favorite) {
        return toPlayerChallengeDto(player, challenge, favorite,
                submissionRepository.findByUserIdOrParticipantUserIdAndChallengeId(player.getId(), challenge.getId()),
                activeGracesByChallenge(player),
                // Le meme departement que la liste : celui du voyage dans le temps
                // s'il y en a un, sinon le sien.
                promoStatesFor(TimeTravelContext.departmentIdOr(player.getDepartment())));
    }

    /**
     * Ou en est la promo sur chaque defi de promo : deja gagne, ou deja engage.
     *
     * <p>Une lecture pour toute la page : l'etat ne depend pas du joueur mais de
     * son departement, et le demander defi par defi ferait une requete par ligne
     * pour une reponse qui ne change pas.</p>
     */
    private Map<UUID, PromoState> promoStatesFor(UUID departmentId) {
        if (departmentId == null) {
            return Map.of();
        }
        Map<UUID, PromoState> states = new HashMap<>();
        for (ChallengeSubmission submission : submissionRepository.findPromoSubmissionsForDepartment(departmentId)) {
            UUID challengeId = submission.getChallenge().getId();
            PromoState current = states.getOrDefault(challengeId, PromoState.OPEN);
            states.put(challengeId, STATUS_VALIDATED.equals(submission.getStatus())
                    ? PromoState.WON
                    : current == PromoState.WON ? PromoState.WON : PromoState.ENGAGED);
        }
        return states;
    }

    /** Ce que la promo a deja fait d'un defi de promo. */
    private enum PromoState {
        /** Personne ne s'y est mis. */
        OPEN,
        /** Une preuve est partie, et attend d'etre jugee. */
        ENGAGED,
        /** Le defi est gagne : il compte comme fait pour toute la promo. */
        WON
    }

    /** Les fins de grâce du joueur, par défi. Une lecture, pas une par ligne. */
    private Map<UUID, Instant> activeGracesByChallenge(AppUser player) {
        return graceRepository.findActiveForUser(player.getId(), TimeTravelContext.instantNow()).stream()
                .collect(Collectors.toMap(
                        grace -> grace.getChallenge().getId(),
                        ChallengeGrace::getExpiresAt,
                        (first, second) -> first.isAfter(second) ? first : second));
    }

    private PlayerChallengeDto toPlayerChallengeDto(
            AppUser player, Challenge challenge, boolean favorite, List<ChallengeSubmission> userSubmissions,
            Map<UUID, Instant> gracesByChallenge, Map<UUID, PromoState> promoStates) {

        int validCount = (int) userSubmissions.stream().filter(s -> STATUS_VALIDATED.equals(s.getStatus())).count();
        boolean hasPending = userSubmissions.stream()
                .anyMatch(s -> STATUS_PENDING.equals(s.getStatus()) || STATUS_WAITING_CONFIRMATION.equals(s.getStatus()));
        boolean completed = validCount > 0;

        // Un defi de promo se lit sur la promo, pas sur le joueur : gagne par un
        // membre, il est fait pour tous ; engage par un membre, il est ferme aux
        // autres tant que la preuve n'a pas ete jugee.
        if (challenge.isPromo()) {
            PromoState promoState = promoStates.getOrDefault(challenge.getId(), PromoState.OPEN);
            completed = promoState == PromoState.WON;
            hasPending = hasPending || promoState == PromoState.ENGAGED;
        }
        // Un défi hors de sa fenêtre reste affiché, mais ne peut plus être complété :
        // c'est ce drapeau que le front consulte pour proposer ou non l'envoi d'une
        // preuve, et validateChallengeActive refuserait la soumission de toute façon.
        // Une grâce rouvre un défi fermé, et seulement fermé : elle rend du temps,
        // elle n'ouvre pas en avance ce qui n'a pas commencé.
        Instant graceUntil = gracesByChallenge.get(challenge.getId());
        boolean openByGrace = graceUntil != null && challengeStarted(challenge);
        boolean canStillComplete = !completed && !hasPending && (challengeActive(challenge) || openByGrace);

        return new PlayerChallengeDto(
                challenge.getId(),
                challenge.getTitle(),
                challenge.getDescription(),
                challenge.getPointsReward(),
                challenge.getCategory(),
                challenge.getCategories(),
                challenge.getValidationMode(),
                proofTypeOf(challenge).name(),
                challenge.getSensitive(),
                challenge.isPromo(),
                challenge.getMinGroupSize(),
                challenge.getMaxGroupSize(),
                validCount,
                hasPending,
                canStillComplete,
                completed,
                favorite,
                challenge.getStartDate(),
                challenge.getEndDate(),
                graceUntil
        );
    }

    private void notifyGroupParticipants(ChallengeSubmission submission, Set<UUID> alreadyAccepted) {
        Challenge challenge = submission.getChallenge();
        Map<String, Object> payload = Map.of(
                "challengeId", challenge.getId().toString(),
                "submissionId", submission.getId().toString(),
                "submitterUserId", submission.getUser().getId().toString(),
                "action", "CONFIRM_GROUP_CHALLENGE"
        );
        String submitterName = displayName(submission.getUser());

        for (AppUser participant : submission.getParticipants()) {
            if (alreadyAccepted.contains(participant.getId())) {
                continue;
            }
            notificationService.notifyUser(
                    participant.getId(),
                    submission.getUser().getId(),
                    NotificationType.CHALLENGE_GROUP_INVITE_RECEIVED,
                    "Invitation à un défi",
                    submitterName + " t'a ajouté au défi \"" + challenge.getTitle() + "\".",
                    payload,
                    "CHALLENGE_GROUP_INVITE",
                    submission.getId()
            );
        }
    }

    private void notifyPendingGroupParticipants(
            ChallengeSubmission submission,
            List<ChallengeSubmissionRepository.ParticipantConfirmationProjection> pendingParticipants
    ) {
        Challenge challenge = submission.getChallenge();
        Map<String, Object> payload = Map.of(
                "challengeId", challenge.getId().toString(),
                "submissionId", submission.getId().toString(),
                "submitterUserId", submission.getUser().getId().toString(),
                "action", "CONFIRM_GROUP_CHALLENGE"
        );
        String submitterName = displayName(submission.getUser());
        Set<UUID> pendingParticipantIds = pendingParticipants.stream()
                .map(ChallengeSubmissionRepository.ParticipantConfirmationProjection::getUserId)
                .collect(Collectors.toSet());

        for (AppUser participant : submission.getParticipants()) {
            if (!pendingParticipantIds.contains(participant.getId())) {
                continue;
            }
            notificationService.notifyUser(
                    participant.getId(),
                    submission.getUser().getId(),
                    NotificationType.CHALLENGE_GROUP_INVITE_RECEIVED,
                    "Invitation à un défi",
                    submitterName + " t'a relancé pour le défi \"" + challenge.getTitle() + "\".",
                    payload,
                    "CHALLENGE_GROUP_INVITE",
                    submission.getId()
            );
        }
    }

    private void notifyGroupInviteAnswer(
            ChallengeSubmission submission,
            AppUser participant,
            NotificationType type
    ) {
        boolean accepted = type == NotificationType.CHALLENGE_GROUP_INVITE_ACCEPTED;
        notificationService.notifyUser(
                submission.getUser().getId(),
                participant.getId(),
                type,
                accepted ? "Invitation acceptée" : "Invitation refusée",
                displayName(participant) + (accepted
                        ? " a accepté de participer au défi \"" + submission.getChallenge().getTitle() + "\"."
                        : " a refusé de participer au défi \"" + submission.getChallenge().getTitle() + "\"."),
                Map.of(
                        "challengeId", submission.getChallenge().getId().toString(),
                        "submissionId", submission.getId().toString(),
                        "participantUserId", participant.getId().toString(),
                        "accepted", accepted
                ),
                "CHALLENGE_GROUP_INVITE",
                submission.getId()
        );
    }

    private void promoteConfirmedGroupSubmission(ChallengeSubmission submission) {
        if (VALIDATION_MODE_AUTO.equals(submission.getChallenge().getValidationMode())) {
            submission.setStatus(STATUS_VALIDATED);
            submission.setModerationComment(null);
            submission.setValidatedAt(fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
        } else {
            submission.setStatus(STATUS_PENDING);
            submission.setModerationComment(null);
            submission.setValidatedAt(null);
        }
    }

    private boolean canModerateSubmission(
            AppUser moderator,
            Collection<String> authorities,
            ChallengeSubmission submission
    ) {
        boolean admin = hasRole(authorities, "ROLE_ADMIN");

        if (admin) {
            return true;
        }

        // Le defi declare sensible par l'organisation, ou la preuve declaree telle par
        // son auteur : dans les deux cas, elle ne se regarde qu'entre personnes du meme
        // genre. Celui qui envoie la photo est parfois seul a savoir ce qu'elle montre.
        if ((Boolean.TRUE.equals(submission.getChallenge().getSensitive())
                || Boolean.TRUE.equals(submission.getSensitive()))
                && !Objects.equals(moderator.getGender(), submission.getUser().getGender())) {
            return false;
        }

        if (hasRole(authorities, "ROLE_RESPOS_DEFIS")) {
            return true;
        }

        if (!hasRole(authorities, "ROLE_SOUS_RESPO_DEFIS") && !hasRole(authorities, "ROLE_RESPO_DEPART")) {
            return false;
        }

        UUID verifyingDepartmentId = moderator.getDepartment();
        UUID verifiedDepartmentId = submission.getUser().getDepartment();

        return verifyingDepartmentId != null
                && verifiedDepartmentId != null
                && verificationLinkRepository.existsByVerifyingDepartmentIdAndVerifiedDepartmentId(
                        verifyingDepartmentId,
                        verifiedDepartmentId
                );
    }

    private AppUser moderatorForAccess(String keycloakSub, Collection<String> authorities) {
        if (hasRole(authorities, "ROLE_ADMIN")) {
            return null;
        }
        return userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Modérateur introuvable"));
    }

    private AppUser moderatorForMessage(String keycloakSub, Collection<String> authorities) {
        return userRepository.findByKeycloakSub(keycloakSub).orElseGet(() -> {
            if (hasRole(authorities, "ROLE_ADMIN")) {
                return null;
            }
            throw new NotFoundException("Modérateur introuvable");
        });
    }

    private void ensureSubmissionMember(ChallengeSubmission submission, AppUser user) {
        if (submissionMembers(submission).stream().noneMatch(member -> member.getId().equals(user.getId()))) {
            throw new ForbiddenException("Accès refusé : vous ne participez pas à cette soumission.");
        }
    }

    private void ensureSubmissionSubmitter(ChallengeSubmission submission, AppUser user) {
        if (!submission.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Accès refusé : vous n'êtes pas l'initiateur de cette soumission.");
        }
    }

    /**
     * Une soumission de groupe encore en attente de reponses.
     *
     * <p>Le statut suffit, et la liste des participants ne dit plus rien : depuis que
     * refuser retire son auteur du groupe, une soumission en attente peut n'avoir
     * plus personne -- c'est precisement l'etat que le chef doit pouvoir resoudre en
     * ajoutant quelqu'un.</p>
     */
    private void ensureWaitingGroupSubmission(ChallengeSubmission submission) {
        if (!STATUS_WAITING_CONFIRMATION.equals(submission.getStatus())) {
            throw new ConflictException("Cette soumission de groupe n'est pas en attente.");
        }
    }

    private void ensureSubmissionCanReceiveMessages(ChallengeSubmission submission) {
        if (STATUS_REJECTED.equals(submission.getStatus())) {
            throw new ConflictException("Impossible de commenter une soumission refusée.");
        }
    }

    private ChallengeSubmissionMessage createSubmissionMessage(
            ChallengeSubmission submission,
            AppUser author,
            String authorKeycloakSub,
            String authorDisplayName,
            String authorType,
            SubmissionMessageCreateDto dto
    ) {
        if (dto == null || dto.content() == null || dto.content().isBlank()) {
            throw new BadRequestException("Le message est requis.");
        }
        String content = dto.content().trim();
        if (content.length() > 2000) {
            throw new BadRequestException("Le message doit contenir au plus 2000 caractères.");
        }

        ChallengeSubmissionMessage message = new ChallengeSubmissionMessage();
        message.setSubmission(submission);
        message.setAuthor(author);
        message.setAuthorKeycloakSub(authorKeycloakSub);
        message.setAuthorDisplayName(authorDisplayName);
        message.setAuthorType(authorType);
        message.setContent(content);
        return message;
    }

    private SubmissionMessageDto toPlayerSubmissionMessageDto(ChallengeSubmissionMessage message, AppUser currentUser) {
        boolean moderator = ChallengeSubmissionMessage.AUTHOR_MODERATOR.equals(message.getAuthorType());
        UUID authorUserId = message.getAuthor() == null ? null : message.getAuthor().getId();
        return new SubmissionMessageDto(
                message.getId(),
                message.getAuthorType(),
                moderator ? null : authorUserId,
                moderator ? "Équipe de modération" : message.getAuthorDisplayName(),
                !moderator && authorUserId != null && authorUserId.equals(currentUser.getId()),
                message.getContent(),
                message.getCreatedAt()
        );
    }

    private SubmissionMessageDto toModerationSubmissionMessageDto(ChallengeSubmissionMessage message, String keycloakSub) {
        UUID authorUserId = message.getAuthor() == null ? null : message.getAuthor().getId();
        return new SubmissionMessageDto(
                message.getId(),
                message.getAuthorType(),
                authorUserId,
                message.getAuthorDisplayName(),
                Objects.equals(message.getAuthorKeycloakSub(), keycloakSub),
                message.getContent(),
                message.getCreatedAt()
        );
    }

    private static boolean hasRole(Collection<String> authorities, String role) {
        return authorities != null && authorities.contains(role);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void ensureReviewLockOwnedBy(ChallengeSubmission submission, String keycloakSub, String message) {
        if (!isReviewLockActive(submission, fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow()) || !reviewLockedBy(submission, keycloakSub)) {
            throw new ConflictException(message);
        }
    }

    private static boolean isReviewLockActive(ChallengeSubmission submission, Instant now) {
        Instant expiresAt = submission.getReviewExpiresAt() != null
                ? submission.getReviewExpiresAt()
                : submission.getReviewStartedAt() == null ? null : submission.getReviewStartedAt().plus(REVIEW_LOCK_TTL);
        return submission.getReviewLockedBy() != null
                && submission.getReviewStartedAt() != null
                && expiresAt != null
                && expiresAt.isAfter(now);
    }

    private static boolean reviewLockedBy(ChallengeSubmission submission, String keycloakSub) {
        return Objects.equals(submission.getReviewLockedBy(), keycloakSub);
    }

    private static String displayName(AppUser user) {
        return user.getDisplayName() != null && !user.getDisplayName().isBlank()
                ? user.getDisplayName()
                : user.getEmail();
    }

    private static void clearReviewLock(ChallengeSubmission submission) {
        submission.setReviewLockedBy(null);
        submission.setReviewStartedAt(null);
        submission.setReviewExpiresAt(null);
    }

    private List<ProofMediaDto> proofMedia(ChallengeSubmission submission) {
        if ("TEXT".equals(submission.getProofType())) return List.of();
        List<String> keys = proofStorageService.parseProofFiles(submission.getProofData());
        return java.util.stream.IntStream.range(0, keys.size())
                .mapToObj(index -> new ProofMediaDto(index, proofStorageService.contentTypeForProofKey(keys.get(index))))
                .toList();
    }

    private PendingSubmissionDto toPendingSubmissionDto(ChallengeSubmission submission, String keycloakSub) {
        boolean reviewLocked = isReviewLockActive(submission, fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow());
        return new PendingSubmissionDto(
                submission.getId(),
                submission.getChallenge().getTitle(),
                submission.getUser().getDisplayName() != null
                        ? submission.getUser().getDisplayName()
                        : submission.getUser().getEmail(),
                submission.getUser().getDepartment() != null
                        ? departmentRepository.findById(submission.getUser().getDepartment()).map(Department::getName).orElse("Aucun")
                        : "Aucun",
                submission.getProofType(),
                "TEXT".equals(submission.getProofType()) ? submission.getProofData() : null,
                proofMedia(submission),
                submissionParticipants(submission),
                submission.getComment(),
                submission.getModerationComment(),
                reviewLocked,
                reviewLocked && reviewLockedBy(submission, keycloakSub),
                reviewLocked ? submission.getReviewStartedAt() : null,
                submission.getCreatedAt()
        );
    }

    private ModerationSubmissionDetailDto toModerationSubmissionDetailDto(
            ChallengeSubmission submission,
            String keycloakSub,
            boolean reviewLocked,
            List<SubmissionMessageDto> messages
    ) {
        Challenge challenge = submission.getChallenge();
        return new ModerationSubmissionDetailDto(
                submission.getId(),
                submission.getStatus(),
                submission.getCreatedAt(),
                submission.getValidatedAt(),
                displayName(submission.getUser()),
                departmentName(submission.getUser()),
                submission.getProofType(),
                "TEXT".equals(submission.getProofType()) ? submission.getProofData() : null,
                proofMedia(submission),
                submission.getProofMediaPurgedAt(),
                submission.getComment(),
                submission.getModerationComment(),
                submission.getBonusPoints() == null ? 0 : submission.getBonusPoints(),
                Boolean.TRUE.equals(submission.getMessagesEnabled()),
                reviewLocked,
                reviewLocked && reviewLockedBy(submission, keycloakSub),
                reviewLocked ? submission.getReviewStartedAt() : null,
                new ModerationSubmissionChallengeDto(
                        challenge.getId(),
                        challenge.getTitle(),
                        challenge.getDescription(),
                        challenge.getPointsReward() == null ? 0 : challenge.getPointsReward(),
                        challenge.getCategory(),
                        challenge.getValidationMode(),
                        // Le badge dit « sensible » des que l'un des deux l'est : le
                        // moderateur n'a pas a savoir lequel pour traiter la preuve avec
                        // le meme soin.
                        Boolean.TRUE.equals(challenge.getSensitive())
                                || Boolean.TRUE.equals(submission.getSensitive()),
                        challenge.getMinGroupSize() == null ? 1 : challenge.getMinGroupSize(),
                        challenge.getMaxGroupSize() == null ? 1 : challenge.getMaxGroupSize()
                ),
                submissionParticipants(submission),
                messages
        );
    }

    private String departmentName(AppUser user) {
        return user.getDepartment() != null
                ? departmentRepository.findById(user.getDepartment()).map(Department::getName).orElse("Aucun")
                : "Aucun";
    }

    private void validateGroupSizes(Challenge challenge) {
        if (challenge.getMinGroupSize() < 1) {
            throw new BadRequestException(
                    "Configuration de groupe invalide.",
                    "minGroupSize",
                    "La taille minimale du groupe doit être supérieure ou égale à 1."
            );
        }

        if (challenge.getMaxGroupSize() < challenge.getMinGroupSize()) {
            throw new BadRequestException(
                    "Configuration de groupe invalide.",
                    "maxGroupSize",
                    "La taille maximale du groupe doit être supérieure ou égale à la taille minimale."
            );
        }
    }

    private void validateChallengeConfig(Challenge challenge) {
        if (!"ADMIN".equals(challenge.getValidationMode()) && !VALIDATION_MODE_AUTO.equals(challenge.getValidationMode())) {
            throw new BadRequestException(
                    "Mode de validation invalide.",
                    "validationMode",
                    "Le mode de validation doit être ADMIN ou AUTO."
            );
        }

        // Une phrase automatique se mesure joueur par joueur : elle validerait le
        // defi pour chacun, la ou un defi de promo n'est a relever qu'une fois. Les
        // deux reglages ensemble ne veulent rien dire.
        if (challenge.isPromo() && VALIDATION_MODE_AUTO.equals(challenge.getValidationMode())) {
            throw new BadRequestException(
                    "Défi de promo invalide.",
                    "promo",
                    "Un défi de promo se valide par une preuve, pas automatiquement."
            );
        }

        if (VALIDATION_MODE_AUTO.equals(challenge.getValidationMode())) {
            autoChallengeTypeRegistry.parseAndValidate(
                    challenge.getAutoChallengeType(), challenge.getAutoChallengeParameters());
        } else {
            challenge.setAutoChallengeType(null);
            challenge.setAutoChallengeParameters(null);
        }

        if (challenge.getPointsReward() == null || challenge.getPointsReward() < 0) {
            throw new BadRequestException(
                    "Récompense de défi invalide.",
                    "pointsReward",
                    "La récompense en points doit être supérieure ou égale à 0."
            );
        }

        if (challenge.getGlobalQuota() != null && challenge.getGlobalQuota() < 0) {
            throw new BadRequestException(
                    "Quota global invalide.",
                    "globalQuota",
                    "Le quota global doit être supérieur ou égal à 0."
            );
        }

        if (challenge.getStartDate() != null
                && challenge.getEndDate() != null
                && challenge.getEndDate().isBefore(challenge.getStartDate())) {
            throw new BadRequestException(
                    "Fenêtre de défi invalide.",
                    "endDate",
                    "La date de fin doit être postérieure ou égale à la date de début."
            );
        }
    }

    /**
     * Annonce qu'une phrase automatique vient d'être écrite, pour que ceux qui la
     * remplissent déjà soient rattrapés sans attendre le balayage horaire.
     *
     * <p>Une phrase porte souvent sur un état ancien — « avoir un ami » quand on en
     * a deux depuis une semaine —, que les déclencheurs ne peuvent pas voir : ils ne
     * parlent que du moment présent.</p>
     */
    /**
     * Où en est ce joueur sur ce défi automatique, ou {@code null}.
     *
     * <p>Vide pour un défi à validation humaine — il n'y a pas de compteur —, et
     * pour un type qui ne sait pas répondre joueur par joueur.</p>
     */
    @Transactional(readOnly = true)
    public ChallengeProgressDto autoChallengeProgress(String keycloakSub, UUID challengeId) {
        AppUser user = userRepository.findByKeycloakSub(keycloakSub)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new NotFoundException("Défi introuvable."));
        if (!VALIDATION_MODE_AUTO.equals(challenge.getValidationMode())) {
            return null;
        }
        // Un defi auto est visible avant son ouverture -- par une date de parution
        // anterieure, ou parce qu'il est quotidien. Annoncer sa progression le fait
        // passer pour actif : la carte promet qu'il « se valide tout seul des que la
        // condition est remplie », et la barre se remplit, alors qu'aucune
        // attribution ne peut tomber avant la date de debut. Barre pleine et rien
        // qui vienne : le defi passe pour casse.
        if (!challengeStarted(challenge)) {
            return null;
        }

        AutoChallengeProgress progress;
        try {
            progress = autoChallengeTypeRegistry.progress(
                    challenge.getAutoChallengeType(), challenge.getAutoChallengeParameters(), user.getId());
        } catch (RuntimeException unreadable) {
            // Une phrase mal configurée ne doit pas casser l'affichage du défi : on
            // n'annonce simplement pas d'avancement.
            return null;
        }
        return progress == null
                ? null
                : new ChallengeProgressDto(
                        progress.current(), progress.target(), progress.percentage(), progress.completed());
    }

    private void publishAutoChallengeSaved(Challenge challenge) {
        if (challenge == null || challenge.getId() == null
                || !VALIDATION_MODE_AUTO.equals(challenge.getValidationMode())) {
            return;
        }
        applicationEventPublisher.publishEvent(new AutoChallengeSavedEvent(challenge.getId()));
    }

    private void setAutomaticChallengeConfiguration(Challenge challenge, ChallengeCreateDto dto) {
        if ("ADMIN".equals(dto.validationMode())) {
            challenge.setAutoChallengeType(null);
            challenge.setAutoChallengeParameters(null);
            return;
        }

        challenge.setAutoChallengeType(dto.autoChallengeType());
        challenge.setAutoChallengeParameters(JsonPayloads.toNode(dto.autoChallengeParameters()));
    }

    /**
     * Pose les categories du defi : la principale, puis les autres.
     *
     * <p>La liste fait foi quand elle est fournie — sa tete devient la principale —
     * et le champ seul continue de marcher pour un client qui ne la connait pas
     * encore. Chaque nom est verifie : une categorie inventee doit se voir a
     * l'enregistrement, pas a l'affichage.</p>
     */
    private void applyCategories(Challenge challenge, ChallengeCreateDto dto) {
        List<String> requested = dto.categories() == null || dto.categories().isEmpty()
                ? List.of(dto.category() == null ? "" : dto.category())
                : dto.categories();

        List<String> resolved = requested.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(this::resolveCategory)
                .distinct()
                .toList();
        if (resolved.isEmpty()) {
            resolved = List.of(resolveCategory(null));
        }

        challenge.setCategory(resolved.getFirst());
        challenge.setExtraCategories(new java.util.HashSet<>(resolved.subList(1, resolved.size())));
    }

    private String resolveCategory(String category) {
        String normalizedCategory = ChallengeCategoryService.normalizeName(category);

        if (!categoryRepository.existsById(normalizedCategory)) {
            throw new BadRequestException(
                    "Catégorie de défi introuvable.",
                    "category",
                    "La catégorie doit exister avant d'être assignée à un défi."
            );
        }

        return normalizedCategory;
    }

    private void validateDailyEndDate(Challenge challenge) {
        if (challenge.hasCategory(ChallengeCategory.DAILY_NAME) && challenge.getEndDate() == null) {
            throw new BadRequestException(
                    "Date de fin requise pour un défi du jour.",
                    "endDate",
                    "Un défi de catégorie DAILY doit avoir une date de fin."
            );
        }
    }

    private void validatePointsAdjustmentForDecision(
            Challenge challenge,
            String newStatus,
            String moderationComment,
            Integer bonusPoints
    ) {
        if (bonusPoints == null || bonusPoints == 0) {
            return;
        }

        if (!STATUS_VALIDATED.equals(newStatus)) {
            throw new BadRequestException(
                    "Points bonus invalides.",
                    "bonusPoints",
                    "Les ajustements de points ne peuvent être appliqués qu'à une soumission validée."
            );
        }

        int baseReward = challenge.getPointsReward() == null ? 0 : challenge.getPointsReward();
        if ((long) baseReward + bonusPoints < 0L) {
            throw new BadRequestException(
                    "Ajustement de points invalide.",
                    "bonusPoints",
                    "L'ajustement ne peut pas rendre la récompense finale négative."
            );
        }

        if (moderationComment == null) {
            throw new BadRequestException(
                    "Commentaire de validation requis.",
                    "moderationComment",
                    "Un commentaire est requis pour contextualiser tout bonus ou malus."
            );
        }
    }

    private void validateChallengeActive(Challenge challenge, Collection<UUID> memberIds) {
        Instant now = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();

        // Sans cette garde, la date de parution ne serait qu'un filtre d'affichage :
        // l'identifiant d'un défi caché suffirait à y soumettre une preuve.
        if (!published(challenge)) {
            throw new BadRequestException("Ce défi n'est pas encore disponible.");
        }

        if (challenge.getStartDate() != null && challenge.getStartDate().isAfter(now)) {
            throw new BadRequestException("Ce défi n'est pas encore disponible.");
        }

        if (challenge.getEndDate() != null && challenge.getEndDate().isBefore(now)) {
            // La grâce ne rouvre le défi qu'à ceux qui l'ont reçue, et tous les
            // membres doivent l'avoir : sans cela, un seul gracié ferait entrer
            // toute une équipe après la fermeture.
            if (!allCoveredByGrace(challenge, memberIds, now)) {
                throw new BadRequestException("Ce défi n'est plus disponible.");
            }
        }
    }

    private boolean allCoveredByGrace(Challenge challenge, Collection<UUID> memberIds, Instant now) {
        if (memberIds == null || memberIds.isEmpty()) {
            return false;
        }
        Set<UUID> graced = graceRepository
                .findActiveForUsersAndChallenge(List.copyOf(memberIds), challenge.getId(), now)
                .stream()
                .map(grace -> grace.getUser().getId())
                .collect(Collectors.toSet());
        return graced.containsAll(memberIds);
    }

    private void validateAllDepartmentsFound(List<UUID> requestedDepartmentIds, List<Department> departments) {
        LinkedHashSet<UUID> departmentIds = requestedDepartmentIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (departments.size() != departmentIds.size()) {
            throw new BadRequestException(
                    "Un ou plusieurs départements ciblés sont introuvables.",
                    "targetDepartmentIds",
                    "Tous les départements ciblés doivent exister."
            );
        }
    }

    private Set<AppUser> resolveParticipants(AppUser submitter, List<UUID> participantUserIds) {
        if (participantUserIds == null || participantUserIds.isEmpty()) {
            return Set.of();
        }

        LinkedHashSet<UUID> requestedIds = participantUserIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        requestedIds.remove(submitter.getId());

        if (requestedIds.isEmpty()) {
            return Set.of();
        }

        List<AppUser> users = userRepository.findAllById(requestedIds);
        if (users.size() != requestedIds.size()) {
            throw new BadRequestException(
                    "Un ou plusieurs membres du groupe sont introuvables.",
                    "participantUserIds",
                    "Tous les membres du groupe doivent exister."
            );
        }

        return new LinkedHashSet<>(users);
    }

    /**
     * Un defi de promo est ferme des la premiere preuve envoyee.
     *
     * <p>Il n'est a relever qu'une fois : laisser trois personnes preparer la meme
     * preuve, c'est en refuser deux apres coup pour un travail deja fait. Une preuve
     * refusee, elle, ne bloque plus rien -- la promo garde sa chance.</p>
     *
     * <p>Doit rester identique a ProofStorageService.validatePromoChallengeOpen : les
     * deux gardent le meme geste par deux chemins, et une divergence rouvrirait l'un
     * des deux a ce que l'autre refuse.</p>
     */
    private void validatePromoChallengeOpen(AppUser submitter, Challenge challenge) {
        UUID departmentId = submitter.getDepartment();
        if (departmentId == null) {
            throw new BadRequestException(
                    "Défi de promo indisponible.",
                    "department",
                    "Ce défi se relève pour un département, et le tien n'est pas renseigné."
            );
        }

        List<ChallengeSubmission> promoSubmissions =
                submissionRepository.findPromoBlockingSubmissions(challenge.getId(), departmentId);

        if (promoSubmissions.stream().anyMatch(submission -> STATUS_VALIDATED.equals(submission.getStatus()))) {
            throw new ConflictException("Ta promo a déjà validé ce défi.");
        }
        if (!promoSubmissions.isEmpty()) {
            throw new ConflictException("Ta promo a déjà envoyé une preuve pour ce défi : elle attend d'être jugée.");
        }
    }

    private void validateMembersCanSubmit(AppUser submitter, Set<AppUser> participants, Challenge challenge) {
        LinkedHashSet<UUID> memberIds = new LinkedHashSet<>();
        memberIds.add(submitter.getId());
        participants.stream().map(AppUser::getId).forEach(memberIds::add);

        validateChallengeActive(challenge, memberIds);
        validateGroupSize(participants, challenge);
        validateMembersCanAccessChallenge(submitter, participants, challenge);
        validateMembersCompletedPrerequisites(submitter, participants, challenge);

        if (challenge.isPromo()) {
            validatePromoChallengeOpen(submitter, challenge);
        }

        List<ChallengeSubmission> blockingSubmissions = submissionRepository.findBlockingSubmissionsForUsersAndChallenge(
                List.copyOf(memberIds),
                challenge.getId()
        );

        // Même raison qu'au-dessus : dire qui bloque, pas seulement que ça bloque.
        blockingSubmissions.stream()
                .filter(submission -> STATUS_WAITING_CONFIRMATION.equals(submission.getStatus()))
                .findFirst()
                .ifPresent(submission -> {
                    throw new ConflictException(blockedMemberMessage(
                            submitter, submission, memberIds, "a déjà une confirmation en attente pour ce défi",
                            "as déjà une confirmation en attente pour ce défi"));
                });

        blockingSubmissions.stream()
                .filter(submission -> STATUS_PENDING.equals(submission.getStatus()))
                .findFirst()
                .ifPresent(submission -> {
                    throw new ConflictException(blockedMemberMessage(
                            submitter, submission, memberIds, "a déjà une soumission en attente pour ce défi",
                            "as déjà une soumission en attente pour ce défi"));
                });

        blockingSubmissions.stream()
                .filter(submission -> STATUS_VALIDATED.equals(submission.getStatus()))
                .findFirst()
                .ifPresent(submission -> {
                    throw new ConflictException(blockedMemberMessage(
                            submitter, submission, memberIds, "a déjà validé ce défi", "as déjà validé ce défi"));
                });
    }

    private void validateMembersCompletedPrerequisites(AppUser submitter, Set<AppUser> participants, Challenge challenge) {
        if (challenge.getPrerequisiteChallenges().isEmpty()) {
            return;
        }

        for (AppUser member : allMembers(submitter, participants)) {
            List<String> missing = missingPrerequisites(member, challenge);
            if (!missing.isEmpty()) {
                // Nommer la personne et les défis qui manquent : « prérequis
                // incomplets » obligeait à ouvrir la console pour savoir qui
                // bloquait, et à deviner ce qu'il lui restait à faire.
                String message = member.getId().equals(submitter.getId())
                        ? "Il te reste à valider " + joinTitles(missing) + " avant ce défi."
                        : memberLabel(member) + " n'a pas encore validé " + joinTitles(missing) + ".";
                throw new BadRequestException(message, "participantUserIds", message);
            }
        }
    }

    private List<String> missingPrerequisites(AppUser user, Challenge challenge) {
        return challenge.getPrerequisiteChallenges().stream()
                .filter(prerequisite -> !submissionRepository.existsValidatedSubmissionForUserAndChallenge(
                        user.getId(), prerequisite.getId()))
                .map(Challenge::getTitle)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private static String joinTitles(List<String> titles) {
        return titles.stream().map(title -> "« " + title + " »").collect(Collectors.joining(", "));
    }

    /**
     * Les prerequis d'un defi, verifies contre un ensemble deja lu.
     *
     * <p>Cette methode interrogeait la base une fois par prerequis, et le chargement
     * paresseux de {@code getPrerequisiteChallenges()} en ajoutait une par defi -
     * {@code findChallengesForDepartment} les ramene desormais avec un
     * {@code @EntityGraph}. Une page de defis ne coute plus de requete par ligne.</p>
     */
    private boolean prerequisitesCompleted(Challenge challenge, Set<UUID> validatedChallengeIds) {
        return challenge.getPrerequisiteChallenges().stream()
                .allMatch(prerequisite -> validatedChallengeIds.contains(prerequisite.getId()));
    }

    private boolean prerequisitesCompleted(AppUser user, Challenge challenge) {
        return challenge.getPrerequisiteChallenges().stream()
                .allMatch(prerequisite -> submissionRepository.existsValidatedSubmissionForUserAndChallenge(
                        user.getId(),
                        prerequisite.getId()
                ));
    }

    private boolean departmentAllowed(AppUser player, Challenge challenge) {
        return challenge.getTargetDepartments().isEmpty()
                || challenge.getTargetDepartments().stream()
                .anyMatch(department -> department.getId().equals(player.getDepartment()));
    }

    /**
     * Éligibilité pleine, fenêtre comprise. C'est elle que consulte l'attribution
     * automatique : sans la condition de date, un défi auto s'attribuerait hors de
     * sa fenêtre.
     */
    public boolean isPlayerEligibleForChallenge(AppUser player, Challenge challenge) {
        return scheduled(challenge) && challengeActive(challenge) && canPlayerSeeChallenge(player, challenge);
    }

    /**
     * Le défi peut-il s'attribuer tout seul en ce moment, quel que soit le joueur ?
     *
     * <p>Programmé, dans sa fenêtre, et rideau levé. Plus strict que la soumission
     * manuelle, qui accepte un défi sans dates : un joueur qui soumet a vu le défi,
     * alors qu'une attribution automatique tombe sans que personne ne l'ait ouvert.
     * Le panneau appelle brouillon un défi à qui il manque une date, et un
     * brouillon ne doit rien valider.</p>
     */
    public boolean isOpenForAutomaticAward(Challenge challenge) {
        return scheduled(challenge) && challengeActive(challenge) && !challengeVisibility.hidden();
    }

    /** Les deux dates posées : ce que le panneau distingue d'un brouillon. */
    private static boolean scheduled(Challenge challenge) {
        return challenge.getStartDate() != null && challenge.getEndDate() != null;
    }

    /**
     * Voir un défi ne dépend pas de sa fenêtre : un défi terminé ou pas encore ouvert
     * reste consultable et peut être mis en favori. Seule la soumission est bornée.
     *
     * À ne pas confondre avec isPlayerEligibleForChallenge, plus stricte, qui garde
     * l'attribution automatique.
     */
    private boolean canPlayerSeeChallenge(AppUser player, Challenge challenge) {
        if (player.getDepartment() == null || !published(challenge)) {
            return false;
        }

        return departmentAllowed(player, challenge) && prerequisitesCompleted(player, challenge);
    }

    private static final ZoneId PARIS_ZONE = ZoneId.of("Europe/Paris");

    /**
     * Un défi sans date de parution s'affiche à partir de sa date d'activation (startDate).
     *
     * Exception pour les défis quotidiens (DAILY) : ils s'ouvrent à la consultation « plus tôt »
     * (dès le début de la journée de la veille en heure de Paris), afin que les joueurs découvrent
     * le défi de demain à l'avance tout en gardant les soumissions verrouillées.
     *
     * Une parution explicite prime toujours si elle est renseignée.
     */
    private boolean published(Challenge challenge) {
        if (challenge.getPublishedAt() != null) {
            return !challenge.getPublishedAt().isAfter(TimeTravelContext.instantNow());
        }

        Instant reference = challenge.getStartDate();
        if (reference == null) {
            return true;
        }

        if (challenge.hasCategory(ChallengeCategory.DAILY_NAME)) {
            // Début de journée de la veille à 00:00 Europe/Paris
            reference = reference.atZone(PARIS_ZONE)
                    .minusDays(1)
                    .toLocalDate()
                    .atStartOfDay(PARIS_ZONE)
                    .toInstant();
        }

        return !reference.isAfter(TimeTravelContext.instantNow());
    }

    /** Le défi a-t-il commencé ? Une grâce ne peut rien rouvrir avant. */
    private boolean challengeStarted(Challenge challenge) {
        Instant now = TimeTravelContext.instantNow();
        return challenge.getStartDate() == null || !challenge.getStartDate().isAfter(now);
    }

    private boolean challengeActive(Challenge challenge) {
        Instant now = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        return (challenge.getStartDate() == null || !challenge.getStartDate().isAfter(now))
                && (challenge.getEndDate() == null || !challenge.getEndDate().isBefore(now));
    }

    private void setPrerequisiteChallenges(Challenge challenge, List<UUID> prerequisiteChallengeIds, UUID challengeId) {
        if (prerequisiteChallengeIds == null || prerequisiteChallengeIds.isEmpty()) {
            challenge.setPrerequisiteChallenges(new LinkedHashSet<>());
            return;
        }

        LinkedHashSet<UUID> prerequisiteIds = prerequisiteChallengeIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (challengeId != null && prerequisiteIds.contains(challengeId)) {
            throw new BadRequestException(
                    "Un défi ne peut pas dépendre de lui-même.",
                    "prerequisiteChallengeIds",
                    "Retirez le défi courant de ses prérequis."
            );
        }

        List<Challenge> prerequisites = challengeRepository.findAllById(prerequisiteIds);
        if (prerequisites.size() != prerequisiteIds.size()) {
            throw new BadRequestException(
                    "Un ou plusieurs défis prérequis sont introuvables.",
                    "prerequisiteChallengeIds",
                    "Tous les défis prérequis doivent exister."
            );
        }

        challenge.setPrerequisiteChallenges(new LinkedHashSet<>(prerequisites));
    }

    private void validateGroupSize(Set<AppUser> participants, Challenge challenge) {
        int groupSize = 1 + participants.size();

        if (groupSize < challenge.getMinGroupSize()) {
            throw new BadRequestException(
                    "Taille de groupe invalide.",
                    "participantUserIds",
                    "Le groupe ne contient pas assez de membres pour ce défi."
            );
        }

        if (groupSize > challenge.getMaxGroupSize()) {
            throw new BadRequestException(
                    "Taille de groupe invalide.",
                    "participantUserIds",
                    "Le groupe contient trop de membres pour ce défi."
            );
        }
    }

    private void validateMembersCanAccessChallenge(AppUser submitter, Set<AppUser> participants, Challenge challenge) {
        if (challenge.getTargetDepartments().isEmpty()) {
            return;
        }

        String allowed = challenge.getTargetDepartments().stream()
                .map(Department::getShortName)
                .filter(Objects::nonNull)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));

        for (AppUser member : allMembers(submitter, participants)) {
            if (!departmentAllowed(member, challenge)) {
                // Sans nom de département lisible, mieux vaut la phrase courte
                // qu'une parenthèse vide.
                String scope = allowed.isBlank() ? "" : " (" + allowed + ")";
                String message = member.getId().equals(submitter.getId())
                        ? (allowed.isBlank()
                                ? "Ce défi n'est pas ouvert à ton département."
                                : "Ce défi est réservé aux départements " + allowed + ".")
                        : memberLabel(member) + " n'est pas dans un département autorisé" + scope + ".";
                throw new BadRequestException(message, "participantUserIds", message);
            }
        }
    }

    /**
     * La soumission bloquante peut appartenir à n'importe quel membre : on
     * cherche celui du groupe qu'elle concerne pour pouvoir le nommer.
     */
    private String blockedMemberMessage(AppUser submitter, ChallengeSubmission submission, Set<UUID> memberIds,
                                        String thirdPerson, String secondPerson) {
        // La soumission bloquante n'est pas tenue de porter ses membres chargés :
        // faute de pouvoir nommer quelqu'un, on reste sur la formule générale
        // plutôt que d'échouer en essayant d'être précis.
        AppUser blocked = submissionMembers(submission).stream()
                .filter(Objects::nonNull)
                .filter(member -> member.getId() != null && memberIds.contains(member.getId()))
                .findFirst()
                .orElse(null);
        if (blocked == null) {
            return "Un membre du groupe " + thirdPerson + ".";
        }
        return blocked.getId().equals(submitter.getId())
                ? "Tu " + secondPerson + "."
                : memberLabel(blocked) + " " + thirdPerson + ".";
    }

    /** Comment désigner un membre à l'écran quand son nom manque. */
    private static String memberLabel(AppUser user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }
        return user.getEmail() != null && !user.getEmail().isBlank() ? user.getEmail() : "Un membre du groupe";
    }

    private static List<AppUser> allMembers(AppUser submitter, Set<AppUser> participants) {
        List<AppUser> members = new java.util.ArrayList<>();
        members.add(submitter);
        members.addAll(participants);
        return members;
    }

    /**
     * La composition du groupe, quand il y en a un.
     *
     * <p>Un defi de groupe valide pour quatre joueurs credite les quatre, et la
     * ligne du registre ne nomme que celui qui a soumis : filtrer sur un des
     * trois autres ne trouve rien. Les noms sont pour l'humain qui relit la
     * ligne, les identifiants pour qu'une requete sur le JSON retrouve un
     * participant - ce que la colonne {@code target_user_id}, singuliere, ne
     * peut pas faire.</p>
     *
     * <p>La composition inclut celui qui a soumis : resolveParticipants le
     * retire explicitement de getParticipants(), et une composition qui
     * oublierait l'auteur ne serait pas le groupe.</p>
     *
     * <p>Sur une soumission solo les deux cles sont absentes plutot que vides -
     * meme traitement que le commentaire de validation manuelle : une cle
     * presente et vide se lit comme une information, son absence dit qu'il n'y
     * avait rien a dire.</p>
     *
     * <p>Cela n'ecrit deliberement pas une ligne par membre. Le registre reste
     * un geste, une ligne ; c'est un compromis accepte.</p>
     */
    private void putGroupComposition(Map<String, Object> details, ChallengeSubmission submission) {
        if (submission.getParticipants().isEmpty()) {
            return;
        }
        List<AppUser> members = submissionMembers(submission);
        details.put("participantNames", members.stream().map(ChallengeService::displayName).toList());
        details.put("participantIds", members.stream().map(member -> member.getId().toString()).toList());
    }

    /**
     * Le soumettant en tete, puis le reste du groupe trie par nom affiche.
     *
     * <p>getParticipants() est un Set : seule la position du soumettant est
     * stable, le reste vient dans un ordre de hachage. Trier la queue rend la
     * ligne du registre deterministe d'un appel a l'autre plutot que de varier
     * a cardinalite et contenu identiques.</p>
     */
    /**
     * Diviser une recompense entre les membres d'une soumission.
     *
     * <p>Le partage porte sur les membres <em>enregistres dans la soumission</em>, jamais
     * sur l'equipe du moment. Les points sont recalcules de zero a chaque validation :
     * partager sur l'equipe actuelle ferait baisser le solde d'un joueur parce qu'un
     * autre a rejoint l'equipe trois jours plus tard, sans que rien ne l'explique.</p>
     *
     * <p>Le reste va aux premiers plutot que de s'evaporer -- dix tokens pour trois
     * joueurs font 4, 3 et 3, pas 3, 3 et 3. L'ordre vient de {@link #submissionMembers},
     * qui place l'auteur puis les participants tries par nom : il ne depend d'aucun etat
     * mutable, donc deux recalculs successifs donnent le meme partage.</p>
     */
    private static void shareReward(long reward, List<AppUser> members, Map<UUID, Long> recalculatedPoints) {
        if (members.isEmpty()) {
            return;
        }

        long share = reward / members.size();
        long remainder = reward % members.size();
        for (int index = 0; index < members.size(); index++) {
            long amount = share + (index < remainder ? 1L : 0L);
            recalculatedPoints.merge(members.get(index).getId(), amount, Long::sum);
        }
    }

    private List<AppUser> submissionMembers(ChallengeSubmission submission) {
        List<AppUser> members = new java.util.ArrayList<>();
        members.add(submission.getUser());
        // (peut être nul sur une soumission dont l'auteur n'est pas chargé)
        List<AppUser> teammates = new java.util.ArrayList<>(submission.getParticipants());
        teammates.sort(java.util.Comparator.comparing(ChallengeService::displayName));
        members.addAll(teammates);
        return members;
    }

    private List<SubmissionParticipantDto> submissionParticipants(ChallengeSubmission submission) {
        return submissionMembers(submission).stream()
                .map(user -> new SubmissionParticipantDto(
                        user.getId(),
                        user.getDisplayName(),
                        user.getEmail(),
                        null,
                        null,
                        null,
                        submission.getUser().getId().equals(user.getId()),
                        false
                ))
                .toList();
    }

    private PlayerSubmissionDetailDto toPlayerSubmissionDetailDto(ChallengeSubmission submission, AppUser currentUser) {
        Challenge challenge = submission.getChallenge();
        return new PlayerSubmissionDetailDto(
                submission.getId(),
                challenge.getId(),
                challenge.getTitle(),
                submission.getStatus(),
                submission.getCreatedAt(),
                submission.getValidatedAt(),
                submission.getModerationComment(),
                submission.getUser().getId().equals(currentUser.getId()),
                submissionParticipantsWithStatuses(submission),
                belowMinimumGroupSize(submission),
                challenge.getMinGroupSize(),
                challenge.getMaxGroupSize()
        );
    }

    /** Le groupe est-il tombe sous ce que le defi exige ? */
    private static boolean belowMinimumGroupSize(ChallengeSubmission submission) {
        return 1 + submission.getParticipants().size() < submission.getChallenge().getMinGroupSize();
    }

    private List<SubmissionParticipantDto> submissionParticipantsWithStatuses(ChallengeSubmission submission) {
        List<SubmissionParticipantDto> participants = new java.util.ArrayList<>();
        AppUser submitter = submission.getUser();
        participants.add(new SubmissionParticipantDto(
                submitter.getId(),
                displayName(submitter),
                submitter.getEmail(),
                "ACCEPTED",
                submission.getCreatedAt(),
                submission.getCreatedAt(),
                true,
                false
        ));

        Set<UUID> scanned = qrJoinedUserIds(submission.getChallenge().getId(), submitter.getId());
        participants.addAll(submissionRepository.findParticipantConfirmations(submission.getId()).stream()
                .map(participant -> new SubmissionParticipantDto(
                        participant.getUserId(),
                        participant.getDisplayName() != null && !participant.getDisplayName().isBlank()
                                ? participant.getDisplayName()
                                : participant.getEmail(),
                        participant.getEmail(),
                        participant.getConfirmationStatus(),
                        participant.getInvitedAt(),
                        participant.getRespondedAt(),
                        false,
                        scanned.contains(participant.getUserId())
                ))
                .toList());
        return participants;
    }

    /** Ceux qui sont entres en scannant le code de ce joueur sur ce defi. */
    public Set<UUID> qrJoinedUserIds(UUID challengeId, UUID submitterId) {
        if (groupInviteRepository == null) {
            return Set.of();
        }
        return groupInviteRepository.findByChallengeIdAndSubmitterId(challengeId, submitterId)
                .map(invite -> Set.copyOf(invite.getJoinedUserIds()))
                .orElseGet(Set::of);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recalculateChallengePoints() {
        Map<UUID, Long> recalculatedPoints = new HashMap<>();
        Map<UUID, Integer> rewardedSubmissionCountsByChallenge = new HashMap<>();

        for (ChallengeSubmission submission : submissionRepository.findValidatedSubmissionsForPointRecalculation()) {
            Challenge challenge = submission.getChallenge();
            if (challenge == null || challenge.getId() == null || !submissionWithinRewardQuota(challenge, rewardedSubmissionCountsByChallenge)) {
                continue;
            }

            // Un defi de promo ne rapporte rien a celui qui l'a porte : ses points
            // vont au departement, que le classement departemental additionne a
            // part. Il compte quand meme comme fait, pour toute la promo.
            if (challenge.isPromo()) {
                continue;
            }

            long pointsReward = (challenge.getPointsReward() == null ? 0L : challenge.getPointsReward())
                    + (submission.getBonusPoints() == null ? 0L : submission.getBonusPoints());
            List<AppUser> members = submissionMembers(submission);

            if (challenge.isRewardShared()) {
                shareReward(pointsReward, members, recalculatedPoints);
                continue;
            }

            for (AppUser member : members) {
                recalculatedPoints.merge(member.getId(), pointsReward, Long::sum);
            }
        }

        userRepository.findAll().forEach(user -> applyChallengePoints(user, recalculatedPoints.get(user.getId())));
    }

    /**
     * Every challenge point is worth one token. Points are recomputed from scratch here,
     * but tokens are a balance the player also spends on packs and STS runs, so the
     * balance moves by the difference with what challenges already granted rather than
     * being overwritten with the total. The balance floors at zero, so points lost after
     * the tokens were spent cannot push it negative.
     */
    private void applyChallengePoints(AppUser user, Long recalculatedPoints) {
        long points = recalculatedPoints == null ? 0L : recalculatedPoints;
        long alreadyGranted = user.getChallengeTokensGranted() == null ? 0L : user.getChallengeTokensGranted();
        long delta = points - alreadyGranted;

        if (delta != 0L) {
            long before = user.getTokens();
            user.setTokens(Math.max(0L, Math.addExact(user.getTokens(), delta)));
            user.setChallengeTokensGranted(points);
            long applied = user.getTokens() - before;
            long eventPoints = user.creditEventPointsFor(fr.insastronaute.webapi.economy.domain.CurrencySource.CHALLENGE, applied);
            currencyLedger.record(user, fr.insastronaute.webapi.economy.domain.CurrencySource.CHALLENGE, applied, eventPoints, null);
        }
        user.setChallengePoints(points);
    }

    private boolean submissionWithinRewardQuota(Challenge challenge, Map<UUID, Integer> rewardedSubmissionCountsByChallenge) {
        int rewardRank = rewardedSubmissionCountsByChallenge.merge(challenge.getId(), 1, Integer::sum);
        Integer globalQuota = challenge.getGlobalQuota();
        return globalQuota == null || globalQuota == 0 || rewardRank <= globalQuota;
    }

    private AdminChallengeDto toAdminChallengeDto(Challenge challenge) {
        return new AdminChallengeDto(
                challenge.getId(),
                challenge.getTitle(),
                challenge.getDescription(),
                challenge.getPointsReward(),
                challenge.isRewardShared(),
                challenge.getCategory(),
                challenge.getCategories(),
                challenge.getStartDate(),
                challenge.getEndDate(),
                challenge.getPublishedAt(),
                challenge.getGlobalQuota(),
                challenge.getValidationMode(),
                proofTypeOf(challenge).name(),
                challenge.getAutoChallengeType(),
                JsonPayloads.toMap(challenge.getAutoChallengeParameters()),
                challenge.getSensitive(),
                challenge.isPromo(),
                challenge.getMinGroupSize(),
                challenge.getMaxGroupSize(),
                challenge.getTargetDepartments().stream()
                        .map(Department::getId)
                        .toList(),
                challenge.getPrerequisiteChallenges().stream()
                        .map(Challenge::getId)
                        .toList(),
                challenge.getCreatedAt(),
                challenge.getUpdatedAt()
        ,
                challenge.getAllowNegativeReward());
    }
}
