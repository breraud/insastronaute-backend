package fr.insastronaute.webapi.leaderboard.service;

import fr.insastronaute.webapi.card.domain.CardCollection;
import fr.insastronaute.webapi.card.domain.CardCollectionSettings;
import fr.insastronaute.webapi.card.service.CollectionPointsCalculator;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import fr.insastronaute.webapi.card.repository.CardCollectionRepository;
import fr.insastronaute.webapi.cardteam.domain.CardTeam;
import fr.insastronaute.webapi.cardteam.domain.CardTeamMember;
import fr.insastronaute.webapi.cardteam.repository.CardTeamMemberRepository;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.friend.repository.FriendshipRepository;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardConfig;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardEntry;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardMetric;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardScope;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardSnapshot;
import fr.insastronaute.webapi.leaderboard.domain.DepartmentScoringFormula;
import fr.insastronaute.webapi.leaderboard.domain.DepartmentLeaderboardSettings;
import fr.insastronaute.webapi.leaderboard.dto.AdminLeaderboardConfigDto;
import fr.insastronaute.webapi.leaderboard.dto.DepartmentLeaderboardDto;
import fr.insastronaute.webapi.leaderboard.dto.DepartmentLeaderboardEntryDto;
import fr.insastronaute.webapi.leaderboard.dto.DepartmentFormulaPreviewDto;
import fr.insastronaute.webapi.leaderboard.dto.DepartmentFormulaPreviewRowDto;
import fr.insastronaute.webapi.leaderboard.dto.FormulaResultDto;
import fr.insastronaute.webapi.leaderboard.dto.LeaderboardConfigUpdateDto;
import fr.insastronaute.webapi.leaderboard.dto.UserLeaderboardDto;
import fr.insastronaute.webapi.leaderboard.dto.UserLeaderboardEntryDto;
import fr.insastronaute.webapi.leaderboard.dto.UserTotalLeaderboardDto;
import fr.insastronaute.webapi.leaderboard.repository.LeaderboardConfigRepository;
import fr.insastronaute.webapi.leaderboard.repository.LeaderboardEntryRepository;
import fr.insastronaute.webapi.leaderboard.repository.LeaderboardSnapshotRepository;
import fr.insastronaute.webapi.leaderboard.repository.DepartmentLeaderboardSettingsRepository;
import fr.insastronaute.webapi.sts.repository.StsRunRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LeaderboardService {

    private static final String ELIGIBILITY_POLICY_VERSION = "students-without-team-staff:v1";

    private final LeaderboardConfigRepository configRepository;
    private final LeaderboardSnapshotRepository snapshotRepository;
    private final LeaderboardEntryRepository entryRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final FriendshipRepository friendshipRepository;
    private final CardCollectionRepository cardCollectionRepository;
    private final CardTeamMemberRepository cardTeamMemberRepository;
    private final StsRunRepository stsRunRepository;
    private final fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpProfileRepository pvpProfileRepository;
    private final fr.insastronaute.webapi.auth.keycloak.KeycloakGroupMembershipGateway keycloakGateway;
    private final fr.insastronaute.webapi.user.service.RealmRoleDirectory roleDirectory;
    private final CollectionPointsCalculator collectionPointsCalculator;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DepartmentLeaderboardSettingsRepository departmentSettingsRepository;
    /** Les defis de promo : des points qui appartiennent a un departement, et a personne. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository challengeSubmissionRepository;
    private final DepartmentScoreCalculator departmentScoreCalculator = new DepartmentScoreCalculator();

    public LeaderboardService(
            LeaderboardConfigRepository configRepository,
            LeaderboardSnapshotRepository snapshotRepository,
            LeaderboardEntryRepository entryRepository,
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            FriendshipRepository friendshipRepository,
            CardCollectionRepository cardCollectionRepository,
            CardTeamMemberRepository cardTeamMemberRepository,
            StsRunRepository stsRunRepository,
            fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpProfileRepository pvpProfileRepository,
            CollectionPointsCalculator collectionPointsCalculator
    ) {
        this(configRepository, snapshotRepository, entryRepository, userRepository, departmentRepository,
                friendshipRepository, cardCollectionRepository, cardTeamMemberRepository, stsRunRepository,
                pvpProfileRepository, collectionPointsCalculator, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LeaderboardService(
            LeaderboardConfigRepository configRepository,
            LeaderboardSnapshotRepository snapshotRepository,
            LeaderboardEntryRepository entryRepository,
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            FriendshipRepository friendshipRepository,
            CardCollectionRepository cardCollectionRepository,
            CardTeamMemberRepository cardTeamMemberRepository,
            StsRunRepository stsRunRepository,
            fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpProfileRepository pvpProfileRepository,
            CollectionPointsCalculator collectionPointsCalculator,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            fr.insastronaute.webapi.auth.keycloak.KeycloakGroupMembershipGateway keycloakGateway,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            fr.insastronaute.webapi.user.service.RealmRoleDirectory roleDirectory
    ) {
        this.configRepository = configRepository;
        this.snapshotRepository = snapshotRepository;
        this.entryRepository = entryRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.friendshipRepository = friendshipRepository;
        this.cardCollectionRepository = cardCollectionRepository;
        this.cardTeamMemberRepository = cardTeamMemberRepository;
        this.stsRunRepository = stsRunRepository;
        this.pvpProfileRepository = pvpProfileRepository;
        this.collectionPointsCalculator = collectionPointsCalculator;
        this.keycloakGateway = keycloakGateway;
        this.roleDirectory = roleDirectory;
    }

    @Transactional(readOnly = true)
    public List<AdminLeaderboardConfigDto> getConfigs() {
        return configRepository.findAll().stream()
                .sorted(Comparator.comparing(LeaderboardConfig::getScope).thenComparing(LeaderboardConfig::getMetric))
                .map(this::toConfigDto)
                .toList();
    }

    @Transactional
    public void updateTotalLeaderboardWeights(Map<LeaderboardMetric, Integer> weights) {
        updateTotalLeaderboardWeights(weights, null);
    }

    /**
     * @param factors ce par quoi les points de chaque metrique sont multiplies avant leur
     *                poids. Nul, les facteurs enregistres restent.
     */
    @Transactional
    public void updateTotalLeaderboardWeights(Map<LeaderboardMetric, Integer> weights, Map<LeaderboardMetric, Integer> factors) {
        // Un facteur nul effacerait la metrique du total : c'est le role du poids, pas le sien.
        if (factors != null && factors.values().stream().anyMatch(factor -> factor == null || factor < 1)) {
            throw new IllegalArgumentException("Le facteur d'une metrique vaut au moins 1.");
        }
        // Seules les metriques qui s'additionnent ont un poids. Un poids pose sur les autres
        // n'aurait aucun effet, et l'accepter en silence laisserait croire le contraire.
        if (weights != null && weights.entrySet().stream().anyMatch(entry ->
                !entry.getKey().countsTowardTotal() && entry.getValue() != null && entry.getValue() != 0)) {
            throw new IllegalArgumentException("Le classement Elo n'entre pas dans le classement total : il n'a pas de poids.");
        }
        // Une boucle plutot que Collectors.toMap, qui leve sur une valeur nulle : un poids nul
        // doit tomber sur le refus explicite ci-dessous, pas sur une NullPointerException.
        Map<LeaderboardMetric, Integer> additiveWeights = null;
        if (weights != null) {
            additiveWeights = new EnumMap<>(LeaderboardMetric.class);
            for (Map.Entry<LeaderboardMetric, Integer> entry : weights.entrySet()) {
                if (entry.getKey() != null && entry.getKey().countsTowardTotal()) {
                    additiveWeights.put(entry.getKey(), entry.getValue());
                }
            }
        }
        List<LeaderboardConfig> userConfigs = configRepository.findAll().stream()
                .filter(config -> config.getScope() == LeaderboardScope.USER)
                .filter(config -> config.getMetric().countsTowardTotal())
                .toList();
        if (additiveWeights == null || !additiveWeights.keySet().containsAll(userConfigs.stream().map(LeaderboardConfig::getMetric).collect(Collectors.toSet()))
                || additiveWeights.values().stream().anyMatch(weight -> weight == null || weight < 0 || weight > 100)
                || additiveWeights.values().stream().mapToInt(Integer::intValue).sum() != 100) {
            throw new IllegalArgumentException("Les poids du classement total doivent couvrir chaque metrique et totaliser 100%.");
        }
        Map<LeaderboardMetric, Integer> appliedWeights = additiveWeights;
        userConfigs.forEach(config -> {
            config.setTotalWeightPercent(appliedWeights.get(config.getMetric()));
            if (factors != null && factors.containsKey(config.getMetric())) {
                config.setTotalScaleFactor(factors.get(config.getMetric()));
            }
        });
        configRepository.saveAll(userConfigs);
    }

    @Transactional
    public AdminLeaderboardConfigDto updateConfig(
            LeaderboardScope scope,
            LeaderboardMetric metric,
            LeaderboardConfigUpdateDto dto
    ) {
        LeaderboardConfig config = getConfig(scope, metric);

        if (dto.refreshDelayMinutes() != null) {
            config.setRefreshDelayMinutes(dto.refreshDelayMinutes());
        }

        if (dto.autoRefreshEnabled() != null) {
            config.setAutoRefreshEnabled(dto.autoRefreshEnabled());
        }
        if (dto.formula() != null) {
            if (scope != LeaderboardScope.DEPARTMENT) {
                throw new IllegalArgumentException("Department scoring formulas only apply to department leaderboards.");
            }
            requireConfiguredHeadcounts(dto.formula());
            config.setFormula(dto.formula());
        }

        return toConfigDto(configRepository.save(config));
    }

    @Transactional
    public List<AdminLeaderboardConfigDto> bulkUpdateConfigs(LeaderboardScope scope, LeaderboardConfigUpdateDto dto) {
        List<LeaderboardConfig> configs = configRepository.findAll().stream()
                .filter(config -> scope == null || config.getScope() == scope)
                .toList();

        for (LeaderboardConfig config : configs) {
            if (dto.refreshDelayMinutes() != null) {
                config.setRefreshDelayMinutes(dto.refreshDelayMinutes());
            }
            if (dto.autoRefreshEnabled() != null) {
                config.setAutoRefreshEnabled(dto.autoRefreshEnabled());
            }
            if (dto.formula() != null) {
                if (config.getScope() != LeaderboardScope.DEPARTMENT) {
                    throw new IllegalArgumentException("Department scoring formulas only apply to department leaderboards.");
                }
                config.setFormula(dto.formula());
            }
        }
        return configRepository.saveAll(configs).stream()
                .sorted(Comparator.comparing(LeaderboardConfig::getScope).thenComparing(LeaderboardConfig::getMetric))
                .map(this::toConfigDto)
                .toList();
    }

    @Transactional
    public int syncDeletedKeycloakUsers() {
        if (keycloakGateway == null) {
            return 0;
        }
        Set<String> activeKeycloakSubs = keycloakGateway.listActiveKeycloakUserIds();
        if (activeKeycloakSubs == null || activeKeycloakSubs.isEmpty()) {
            return 0;
        }

        List<AppUser> usersToHide = userRepository.findAll().stream()
                .filter(u -> u.getKeycloakSub() != null && !activeKeycloakSubs.contains(u.getKeycloakSub()))
                .filter(u -> !Boolean.TRUE.equals(u.getHiddenFromLeaderboards()))
                .toList();

        for (AppUser user : usersToHide) {
            user.setHiddenFromLeaderboards(true);
        }
        if (!usersToHide.isEmpty()) {
            userRepository.saveAll(usersToHide);
        }
        return usersToHide.size();
    }

    @Transactional
    public void release(LeaderboardScope scope, LeaderboardMetric metric) {
        syncDeletedKeycloakUsers();
        LeaderboardConfig config = getConfig(scope, metric);
        release(config);
    }

    @Transactional
    public void releaseAll(LeaderboardScope scope) {
        syncDeletedKeycloakUsers();
        configRepository.findAll().stream()
                .filter(config -> scope == null || config.getScope() == scope)
                .forEach(this::release);
    }

    /**
     * Les classements dont la publication automatique est due.
     *
     * <p>Rendus par identifiant et non publies ici : chacun se publie ensuite dans sa propre
     * transaction, par {@link #releaseIfDue(UUID)}. Tout publier d'un bloc faisait qu'un seul
     * classement en echec -- un departement sans effectif theorique, par exemple -- annulait
     * aussi la publication de tous les autres, a chaque passage, sans que rien ne bouge nulle
     * part.</p>
     */
    @Transactional(readOnly = true)
    public List<UUID> dueLeaderboardIds() {
        Instant now = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();

        return configRepository.findAll().stream()
                .filter(config -> Boolean.TRUE.equals(config.getAutoRefreshEnabled()))
                .filter(config -> isDueForRelease(config, now))
                .map(LeaderboardConfig::getId)
                .toList();
    }

    /**
     * Publie un classement s'il est toujours du, relu dans cette transaction.
     *
     * @return le classement publie, sous la forme « scope/metrique », ou null s'il ne l'etait plus
     */
    @Transactional
    public String releaseIfDue(UUID configId) {
        Instant now = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        return configRepository.findById(configId)
                .filter(config -> Boolean.TRUE.equals(config.getAutoRefreshEnabled()))
                .filter(config -> isDueForRelease(config, now))
                .map(config -> {
                    release(config);
                    return config.getScope() + "/" + config.getMetric();
                })
                .orElse(null);
    }

    /** Le nom lisible d'un classement, pour un journal d'erreur. */
    @Transactional(readOnly = true)
    public String describeLeaderboard(UUID configId) {
        return configRepository.findById(configId)
                .map(config -> config.getScope() + "/" + config.getMetric())
                .orElse(String.valueOf(configId));
    }

    private LeaderboardSnapshot release(LeaderboardConfig config) {
        LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
        Instant releasedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();

        snapshot.setConfig(config);
        snapshot.setReleasedAt(releasedAt);
        if (config.getScope() == LeaderboardScope.DEPARTMENT) {
            snapshot.setEligibilityFingerprint(currentEligibilityFingerprint());
        }
        LeaderboardSnapshot savedSnapshot = snapshotRepository.save(snapshot);

        List<LeaderboardEntry> entries = switch (config.getScope()) {
            case USER -> buildUserEntries(savedSnapshot, config.getMetric());
            case DEPARTMENT -> buildDepartmentEntries(savedSnapshot, config.getMetric(), config.getFormula());
        };

        entryRepository.saveAll(entries);
        config.setLastReleasedAt(releasedAt);
        configRepository.save(config);
        return savedSnapshot;
    }

    private boolean isDueForRelease(LeaderboardConfig config, Instant now) {
        if (config.getLastReleasedAt() == null) {
            return true;
        }

        return !config.getLastReleasedAt()
                .plusSeconds(config.getRefreshDelayMinutes() * 60L)
                .isAfter(now);
    }

    @Transactional
    public List<UserLeaderboardDto> getLatestUserLeaderboards(List<LeaderboardMetric> requestedMetrics) {
        Set<UUID> hiddenUserIds = hiddenUserIds();
        return normalizeMetrics(LeaderboardScope.USER, requestedMetrics).stream()
                .map(metric -> toUserLeaderboardDto(getLatestSnapshot(LeaderboardScope.USER, metric), metric, hiddenUserIds))
                .toList();
    }

    @Transactional
    public List<UserLeaderboardDto> getLatestUserLeaderboards(
            List<LeaderboardMetric> requestedMetrics,
            AppUser requester
    ) {
        Set<UUID> hiddenUserIds = hiddenUserIds();
        Set<UUID> visibleUserIds = Set.copyOf(friendshipRepository.findAcceptedFriendIds(requester.getId()));

        return normalizeMetrics(LeaderboardScope.USER, requestedMetrics).stream()
                .map(metric -> toUserLeaderboardDto(
                        getLatestSnapshot(LeaderboardScope.USER, metric),
                        metric,
                        requester.getId(),
                        visibleUserIds,
                        hiddenUserIds
                ))
                .toList();
    }

    @Transactional
    public UserTotalLeaderboardDto getLatestUserTotalLeaderboard(AppUser requester) {
        // Charger les IDs des comptes de service (hiddenFromLeaderboards) pour les exclure
        // même si des snapshots existants les contiennent encore.
        Set<UUID> hiddenUserIds = hiddenUserIds();

        Map<UUID, TotalScore> scoresByUserId = new HashMap<>();
        Instant calculatedAt = null;

        for (LeaderboardMetric metric : LeaderboardMetric.values()) {
            // Avant getConfig : un classement qui ne s'additionne pas n'a rien a faire ici.
            if (!metric.countsTowardTotal()) {
                continue;
            }
            LeaderboardConfig config = getConfig(LeaderboardScope.USER, metric);
            LeaderboardSnapshot snapshot = getLatestSnapshot(config);
            if (calculatedAt == null || snapshot.getReleasedAt().isAfter(calculatedAt)) {
                calculatedAt = snapshot.getReleasedAt();
            }

            for (LeaderboardEntry entry : entryRepository.findBySnapshotOrderByRankAsc(snapshot)) {
                if (hiddenUserIds.contains(entry.getSubjectId())) {
                    continue;
                }
                scoresByUserId.computeIfAbsent(
                        entry.getSubjectId(),
                        userId -> new TotalScore(
                                userId,
                                entry.getName(),
                                entry.getDepartment()
                        )
                ).add(weightedScore(entry.getScore(), config));
            }
        }

        List<TotalScore> rankedScores = scoresByUserId.values().stream()
                .sorted(Comparator
                        .comparingLong(TotalScore::score).reversed()
                        .thenComparing(score -> score.name().toLowerCase())
                        .thenComparing(TotalScore::id))
                .toList();
        Set<UUID> friendIds = Set.copyOf(friendshipRepository.findAcceptedFriendIds(requester.getId()));
        List<UserLeaderboardEntryDto> entries = java.util.stream.IntStream.range(0, rankedScores.size())
                .filter(index -> index < 10
                        || rankedScores.get(index).id().equals(requester.getId())
                        || friendIds.contains(rankedScores.get(index).id()))
                .mapToObj(index -> {
                    TotalScore total = rankedScores.get(index);
                    return new UserLeaderboardEntryDto(
                            index + 1,
                            total.name(),
                            total.department(),
                            total.score(),
                            total.id().equals(requester.getId()),
                            friendIds.contains(total.id()),
                            // Le total tous classements confondus se compte par joueur :
                            // aucune de ses lignes ne represente une equipe.
                            List.of()
                    );
                })
                .toList();

        return new UserTotalLeaderboardDto(calculatedAt, entries);
    }

    @Transactional(readOnly = true)
    public UserTotalLeaderboardDto getAdminUserTotalLeaderboard(boolean live) {
        return live ? getLiveAdminUserTotalLeaderboard() : getSnapshotAdminUserTotalLeaderboard();
    }

    private UserTotalLeaderboardDto getLiveAdminUserTotalLeaderboard() {
        Instant calculatedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        Map<UUID, String> departments = departmentShortNamesById();
        List<LeaderboardConfig> configs = configRepository.findAll().stream()
                .filter(config -> config.getScope() == LeaderboardScope.USER)
                .filter(config -> config.getMetric().countsTowardTotal())
                .toList();
        Map<LeaderboardMetric, Map<UUID, Long>> collectionScores = new EnumMap<>(LeaderboardMetric.class);

        List<TotalScore> rankedScores = userRepository.findAll().stream()
                .filter(this::rankable)
                .map(user -> {
                    long total = 0;
                    for (LeaderboardConfig config : configs) {
                        Map<UUID, Long> scores = collectionScores.computeIfAbsent(
                                config.getMetric(), this::collectionScores);
                        total += weightedScore(score(config.getMetric(), user, scores), config);
                    }
                    TotalScore totalScore = new TotalScore(
                            user.getId(),
                            displayName(user),
                            user.getDepartment() == null ? null : departments.get(user.getDepartment()));
                    totalScore.add(total);
                    return totalScore;
                })
                .sorted(totalScoreComparator())
                .toList();

        return new UserTotalLeaderboardDto(calculatedAt, completeTotalEntries(rankedScores));
    }

    private UserTotalLeaderboardDto getSnapshotAdminUserTotalLeaderboard() {
        Set<UUID> hiddenUserIds = hiddenUserIds();
        Map<UUID, TotalScore> scoresByUserId = new HashMap<>();
        Instant calculatedAt = null;

        for (LeaderboardMetric metric : LeaderboardMetric.values()) {
            // Avant getConfig : un classement qui ne s'additionne pas n'a rien a faire ici.
            if (!metric.countsTowardTotal()) {
                continue;
            }
            LeaderboardConfig config = getConfig(LeaderboardScope.USER, metric);
            LeaderboardSnapshot snapshot = getLatestSnapshot(config);
            if (calculatedAt == null || snapshot.getReleasedAt().isAfter(calculatedAt)) {
                calculatedAt = snapshot.getReleasedAt();
            }
            for (LeaderboardEntry entry : entryRepository.findBySnapshotOrderByRankAsc(snapshot)) {
                if (hiddenUserIds.contains(entry.getSubjectId())) continue;
                scoresByUserId.computeIfAbsent(entry.getSubjectId(), userId -> new TotalScore(
                                userId, entry.getName(), entry.getDepartment()))
                        .add(weightedScore(entry.getScore(), config));
            }
        }

        List<TotalScore> rankedScores = scoresByUserId.values().stream()
                .sorted(totalScoreComparator())
                .toList();
        return new UserTotalLeaderboardDto(calculatedAt, completeTotalEntries(rankedScores));
    }

    /**
     * Ce qu'une metrique apporte au total : ses points, multiplies par son facteur puis par
     * son poids. Le seul endroit ou le total se calcule, pour que le total public, celui du
     * panneau en direct et celui des snapshots ne divergent pas.
     */
    private static long weightedScore(long score, LeaderboardConfig config) {
        int factor = config.getTotalScaleFactor() == null ? 1 : config.getTotalScaleFactor();
        return BigDecimal.valueOf(score)
                .multiply(BigDecimal.valueOf(factor))
                .multiply(BigDecimal.valueOf(config.getTotalWeightPercent()))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValue();
    }

    private static Comparator<TotalScore> totalScoreComparator() {
        return Comparator.comparingLong(TotalScore::score).reversed()
                .thenComparing(score -> score.name().toLowerCase())
                .thenComparing(TotalScore::id);
    }

    private static List<UserLeaderboardEntryDto> completeTotalEntries(List<TotalScore> scores) {
        return java.util.stream.IntStream.range(0, scores.size())
                .mapToObj(index -> {
                    TotalScore total = scores.get(index);
                    return new UserLeaderboardEntryDto(index + 1, total.name(), total.department(),
                            total.score(), false, false, List.of());
                })
                .toList();
    }

    @Transactional
    public List<DepartmentLeaderboardDto> getLatestDepartmentLeaderboards(List<LeaderboardMetric> requestedMetrics) {
        return normalizeMetrics(LeaderboardScope.DEPARTMENT, requestedMetrics).stream()
                .map(metric -> toDepartmentLeaderboardDto(getCurrentDepartmentSnapshot(metric), metric))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserLeaderboardDto> getLiveUserLeaderboards(List<LeaderboardMetric> requestedMetrics) {
        Instant now = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        Map<UUID, String> departments = departmentShortNamesById();

        return normalizeMetrics(LeaderboardScope.USER, requestedMetrics).stream()
                .map(metric -> {
                    Map<UUID, Long> collectionScores = collectionScores(metric);
                    List<AppUser> rankedUsers = userRepository.findAll().stream()
                            .filter(this::rankable)
                            .filter(user -> metric == null || !metric.ranksOnlyScoredPlayers() || collectionScores.containsKey(user.getId()))
                            .sorted(Comparator
                                    .comparingLong((AppUser user) -> score(metric, user, collectionScores)).reversed()
                                    .thenComparing(this::normalizedDisplayName)
                                    .thenComparing(AppUser::getId))
                            .toList();

                    // L'apercu en direct passe par le meme regroupement que le
                    // cliche : deux classements de collection qui ne compteraient
                    // pas pareil seraient pires qu'un seul en retard.
                    List<LeaderboardEntry> liveEntries = rankedUsers.stream()
                            .map(user -> {
                                LeaderboardEntry entry = new LeaderboardEntry();
                                entry.setSubjectId(user.getId());
                                entry.setName(displayName(user));
                                entry.setDepartment(user.getDepartment() == null
                                        ? null : departments.get(user.getDepartment()));
                                entry.setScore(score(metric, user, collectionScores));
                                return entry;
                            })
                            .toList();

                    List<RankedRow> rows = groupByTeam(liveEntries, metric);
                    List<UserLeaderboardEntryDto> entries = new ArrayList<>();
                    for (int i = 0; i < rows.size(); i++) {
                        RankedRow row = rows.get(i);
                        entries.add(new UserLeaderboardEntryDto(
                                i + 1,
                                row.name(),
                                row.department(),
                                row.score(),
                                false,
                                false,
                                row.memberNames()
                        ));
                    }
                    return new UserLeaderboardDto(metric, now, entries);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DepartmentLeaderboardDto> getLiveDepartmentLeaderboards(List<LeaderboardMetric> requestedMetrics) {
        Instant now = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        Map<UUID, String> departments = departmentShortNamesById();

        return normalizeMetrics(LeaderboardScope.DEPARTMENT, requestedMetrics).stream()
                .map(metric -> {
                    DepartmentScoringFormula formula = configRepository.findByScopeAndMetric(LeaderboardScope.DEPARTMENT, metric)
                            .map(LeaderboardConfig::getFormula)
                            .orElse(DepartmentScoringFormula.RAW_TOTAL);
                    List<DepartmentScore> rankedDepartments = rankedDepartmentScores(metric, formula, departments);

                    List<DepartmentLeaderboardEntryDto> entries = new ArrayList<>();
                    for (int i = 0; i < rankedDepartments.size(); i++) {
                        DepartmentScore dept = rankedDepartments.get(i);
                        entries.add(new DepartmentLeaderboardEntryDto(
                                i + 1,
                                dept.name(),
                                dept.score(),
                                dept.rawTotal(),
                                dept.memberCount(),
                                dept.theoreticalHeadcount(),
                                dept.coefficient(),
                                dept.promoPoints(),
                                dept.perfCount()
                        ));
                    }
                    Integer reference = rankedDepartments.isEmpty() ? null : rankedDepartments.getFirst().referenceHeadcount();
                    Optional<LeaderboardConfig> totalConfig = totalConfig(metric);
                    return new DepartmentLeaderboardDto(metric, now, formula.name(), reference,
                            totalConfig.map(LeaderboardConfig::getTotalWeightPercent).orElse(null),
                            totalConfig.map(LeaderboardConfig::getTotalScaleFactor).orElse(null),
                            entries);
                })
                .toList();
    }

    /** Coefficient courant du classement Defis, indexe par departement. */
    @Transactional(readOnly = true)
    public Map<UUID, Double> currentDepartmentCoefficients() {
        LeaderboardMetric metric = LeaderboardMetric.CHALLENGE_POINTS;
        DepartmentScoringFormula formula = configRepository
                .findByScopeAndMetric(LeaderboardScope.DEPARTMENT, metric)
                .map(LeaderboardConfig::getFormula)
                .orElse(DepartmentScoringFormula.RAW_TOTAL);
        Map<UUID, DepartmentScore> totals = rawDepartmentScores(metric, departmentShortNamesById());
        Map<UUID, Integer> headcounts = configuredHeadcounts();
        int referenceHeadcount = headcounts.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        double baseline = formula == DepartmentScoringFormula.BALANCED_SQRT
                ? totals.values().stream().mapToDouble(total -> departmentScoreCalculator.calculate(
                        total.score(), total.memberCount(), headcounts.getOrDefault(total.id(), 1),
                        referenceHeadcount, formula).coefficient()).min().orElse(1d)
                : 1d;
        Map<UUID, Double> coefficients = new HashMap<>();
        for (DepartmentScore total : totals.values()) {
            double coefficient = departmentScoreCalculator.calculate(total.score(), total.memberCount(),
                    headcounts.getOrDefault(total.id(), 1), referenceHeadcount, formula).coefficient();
            coefficients.put(total.id(), formula == DepartmentScoringFormula.BALANCED_SQRT
                    ? departmentScoreCalculator.normalizeBalancedCoefficient(coefficient, baseline)
                    : coefficient);
        }
        return coefficients;
    }

    public boolean contributesToLeaderboards(AppUser user) {
        return rankable(user);
    }

    /**
     * Administrative live simulation. It intentionally includes empty departments so
     * an administrator can see which population entries are still missing.
     */
    @Transactional(readOnly = true)
    public DepartmentFormulaPreviewDto getDepartmentFormulaPreview(LeaderboardMetric metric) {
        List<Department> departments = departmentRepository.findAllByVirtualFalse().stream()
                .sorted(Comparator.comparing(Department::getShortName, Comparator.nullsLast(String::compareTo)))
                .toList();
        Map<UUID, Department> departmentById = departments.stream()
                .collect(Collectors.toMap(Department::getId, department -> department));
        Map<UUID, DepartmentScore> totals = rawDepartmentScores(metric, departments.stream()
                .collect(Collectors.toMap(Department::getId, Department::getShortName)));
        Map<UUID, Integer> headcounts = configuredHeadcounts();
        int referenceHeadcount = headcounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<UUID> missing = totals.values().stream()
                .filter(total -> total.memberCount() > 0 && !headcounts.containsKey(total.id()))
                .map(DepartmentScore::id)
                .sorted()
                .toList();
        DepartmentScoringFormula currentFormula = configRepository.findByScopeAndMetric(LeaderboardScope.DEPARTMENT, metric)
                .map(LeaderboardConfig::getFormula)
                .orElse(DepartmentScoringFormula.RAW_TOTAL);

        Map<DepartmentScoringFormula, Map<UUID, FormulaResultDto>> formulas = new EnumMap<>(DepartmentScoringFormula.class);
        for (DepartmentScoringFormula formula : DepartmentScoringFormula.values()) {
            Map<UUID, DepartmentScoreResult> calculated = new HashMap<>();
            for (Department department : departments) {
                DepartmentScore total = totals.getOrDefault(department.getId(), new DepartmentScore(department.getId(), department.getShortName()));
                Integer expected = headcounts.get(department.getId());
                if (formula != DepartmentScoringFormula.RAW_TOTAL && (expected == null || referenceHeadcount == 0)) {
                    continue;
                }
                calculated.put(department.getId(), departmentScoreCalculator.calculate(
                        total.score(), total.memberCount(), expected == null ? 1 : expected,
                        referenceHeadcount == 0 ? 1 : referenceHeadcount, formula));
            }
            Map<UUID, FormulaResultDto> results = new HashMap<>();
            double balancedNormalization = formula == DepartmentScoringFormula.BALANCED_SQRT
                    ? calculated.entrySet().stream()
                    .filter(entry -> totals.getOrDefault(entry.getKey(), new DepartmentScore(entry.getKey(), "")).memberCount() > 0)
                    .mapToDouble(entry -> entry.getValue().coefficient())
                    .min().orElse(1d)
                    : 1d;
            calculated.entrySet().stream()
                    .filter(entry -> totals.getOrDefault(entry.getKey(), new DepartmentScore(entry.getKey(), "")).memberCount() > 0)
                    .sorted(Comparator.comparingLong((Map.Entry<UUID, DepartmentScoreResult> entry) -> entry.getValue().finalScore()).reversed()
                            .thenComparing(entry -> departmentById.get(entry.getKey()).getShortName(), Comparator.nullsLast(String::compareTo))
                            .thenComparing(Map.Entry::getKey))
                    .forEachOrdered(new java.util.function.Consumer<>() {
                        private int rank;
                        @Override public void accept(Map.Entry<UUID, DepartmentScoreResult> entry) {
                            DepartmentScoreResult result = entry.getValue();
                            double coefficient = formula == DepartmentScoringFormula.BALANCED_SQRT
                                    ? departmentScoreCalculator.normalizeBalancedCoefficient(result.coefficient(), balancedNormalization)
                                    : result.coefficient();
                            long finalScore = formula == DepartmentScoringFormula.BALANCED_SQRT
                                    ? BigDecimal.valueOf(result.rawTotal()).multiply(BigDecimal.valueOf(coefficient))
                                    .setScale(0, RoundingMode.HALF_UP).longValue()
                                    : result.finalScore();
                            results.put(entry.getKey(), new FormulaResultDto(coefficient, finalScore, ++rank));
                        }
                    });
            calculated.forEach((id, result) -> {
                double coefficient = formula == DepartmentScoringFormula.BALANCED_SQRT
                        ? departmentScoreCalculator.normalizeBalancedCoefficient(result.coefficient(), balancedNormalization)
                        : result.coefficient();
                long finalScore = formula == DepartmentScoringFormula.BALANCED_SQRT
                        ? BigDecimal.valueOf(result.rawTotal()).multiply(BigDecimal.valueOf(coefficient))
                        .setScale(0, RoundingMode.HALF_UP).longValue()
                        : result.finalScore();
                results.putIfAbsent(id, new FormulaResultDto(coefficient, finalScore, null));
            });
            formulas.put(formula, results);
        }

        List<DepartmentFormulaPreviewRowDto> rows = departments.stream().map(department -> {
            DepartmentScore total = totals.getOrDefault(department.getId(), new DepartmentScore(department.getId(), department.getShortName()));
            Integer expected = headcounts.get(department.getId());
            DepartmentScoreResult raw = departmentScoreCalculator.calculate(total.score(), total.memberCount(), expected == null ? 1 : expected,
                    referenceHeadcount == 0 ? 1 : referenceHeadcount, DepartmentScoringFormula.RAW_TOTAL);
            Map<DepartmentScoringFormula, FormulaResultDto> resultByFormula = new EnumMap<>(DepartmentScoringFormula.class);
            for (DepartmentScoringFormula formula : DepartmentScoringFormula.values()) {
                resultByFormula.put(formula, formulas.get(formula).get(department.getId()));
            }
            return new DepartmentFormulaPreviewRowDto(department.getId(), department.getName(), department.getShortName(), expected,
                    total.memberCount(), raw.fillRate(), total.score(), raw.average(), resultByFormula);
        }).toList();
        return new DepartmentFormulaPreviewDto(metric, referenceHeadcount, DepartmentScoringFormula.BALANCED_SQRT,
                currentFormula, missing, rows);
    }

    private List<LeaderboardEntry> buildUserEntries(LeaderboardSnapshot snapshot, LeaderboardMetric metric) {
        Map<UUID, String> departments = departmentShortNamesById();
        Map<UUID, Long> collectionScores = collectionScores(metric);
        List<AppUser> rankedUsers = userRepository.findAll().stream()
                .filter(this::rankable)
                // Voir LeaderboardMetric#ranksOnlyScoredPlayers : sans classement, pas de ligne. La
                // metrique peut etre nulle ici (cliche a zero), et score() l'accepte deja.
                .filter(user -> metric == null || !metric.ranksOnlyScoredPlayers() || collectionScores.containsKey(user.getId()))
                .sorted(Comparator
                        .comparingLong((AppUser user) -> score(metric, user, collectionScores)).reversed()
                        .thenComparing(this::normalizedDisplayName)
                        .thenComparing(AppUser::getId))
                .toList();

        List<LeaderboardEntry> entries = new ArrayList<>();

        for (int index = 0; index < rankedUsers.size(); index++) {
            AppUser user = rankedUsers.get(index);
            LeaderboardEntry entry = new LeaderboardEntry();

            entry.setSnapshot(snapshot);
            entry.setRank(index + 1);
            entry.setSubjectId(user.getId());
            entry.setName(displayName(user));
            entry.setDepartment(user.getDepartment() == null ? null : departments.get(user.getDepartment()));
            entry.setScore(score(metric, user, collectionScores));
            entries.add(entry);
        }

        return entries;
    }

    private List<DepartmentScore> rankedDepartmentScores(
            LeaderboardMetric metric,
            DepartmentScoringFormula formula,
            Map<UUID, String> departments
    ) {
        Map<UUID, DepartmentScore> totals = rawDepartmentScores(metric, departments);
        Map<UUID, Integer> headcounts = configuredHeadcounts();
        int referenceHeadcount = headcounts.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        if (formula != DepartmentScoringFormula.RAW_TOTAL && totals.keySet().stream().anyMatch(id -> !headcounts.containsKey(id))) {
            throw new ConflictException("Un effectif theorique manque pour un departement participant.");
        }
        double balancedNormalization = formula == DepartmentScoringFormula.BALANCED_SQRT
                ? totals.values().stream()
                .mapToDouble(total -> departmentScoreCalculator.calculate(total.score(), total.memberCount(),
                        headcounts.getOrDefault(total.id(), 1), referenceHeadcount, formula).coefficient())
                .min().orElse(1d)
                : 1d;
        // Les defis de promo s'ajoutent au score arrete, sans passer la formule : un
        // defi releve une fois par promo vaut la meme chose pour une promo de trente
        // et une de deux cents, et le diviser par l'effectif le nierait.
        Map<UUID, Long> promoPoints = metric == LeaderboardMetric.CHALLENGE_POINTS
                ? promoAwardsByDepartment()
                : Map.of();
        List<DepartmentScore> ranked = totals.values().stream()
                .filter(score -> score.name() != null)
                .map(total -> {
                    int expected = headcounts.getOrDefault(total.id(), 1);
                    DepartmentScoreResult result = departmentScoreCalculator.calculate(total.score(), total.memberCount(), expected, referenceHeadcount, formula);
                    long finalScore = formula == DepartmentScoringFormula.BALANCED_SQRT
                            ? BigDecimal.valueOf(total.score()).multiply(BigDecimal.valueOf(departmentScoreCalculator.normalizeBalancedCoefficient(result.coefficient(), balancedNormalization)))
                            .setScale(0, RoundingMode.HALF_UP).longValue()
                            : result.finalScore();
                    // Le detail part avec le score : le classement publie pourra montrer
                    // d'ou il vient sans etre recalcule.
                    double coefficient = formula == DepartmentScoringFormula.BALANCED_SQRT
                            ? departmentScoreCalculator.normalizeBalancedCoefficient(result.coefficient(), balancedNormalization)
                            : result.coefficient();
                    long promo = promoPoints.getOrDefault(total.id(), 0L);
                    return new DepartmentScore(total.id(), total.name(), finalScore + promo, total.memberCount(),
                            total.perfCount(), total.score(), headcounts.get(total.id()), coefficient, promo,
                            referenceHeadcount);
                })
                .collect(Collectors.toCollection(java.util.ArrayList::new));

        // Une promo dont aucun membre ne figure au classement -- tous masques, ou
        // aucun encore inscrit -- a quand meme releve son defi : ses points ne
        // doivent pas disparaitre avec ses joueurs.
        promoPoints.forEach((departmentId, points) -> {
            if (departments.get(departmentId) != null
                    && ranked.stream().noneMatch(score -> departmentId.equals(score.id()))) {
                ranked.add(new DepartmentScore(departmentId, departments.get(departmentId), points, 0, 0,
                        0L, headcounts.get(departmentId), formula == DepartmentScoringFormula.RAW_TOTAL ? 1d : 0d,
                        points, referenceHeadcount));
            }
        });

        ranked.sort(Comparator.comparingLong(DepartmentScore::score).reversed()
                .thenComparing(DepartmentScore::name).thenComparing(DepartmentScore::id));
        return List.copyOf(ranked);
    }

    /**
     * Le total des joueurs de chaque departement, avant formule.
     *
     * <p>Les defis de promo n'entrent pas ici : ils ne passent pas la formule, et
     * s'ajoutent au score une fois celle-ci appliquee. Voir
     * {@link #rankedDepartmentScores}.</p>
     */
    private Map<UUID, DepartmentScore> rawDepartmentScores(LeaderboardMetric metric, Map<UUID, String> departments) {
        Map<UUID, DepartmentScore> totals = new HashMap<>();
        Map<UUID, Long> collectionScores = collectionScores(metric);
        userRepository.findAll().stream()
                .filter(this::rankable)
                .filter(user -> user.getDepartment() != null)
                .forEach(user -> {
                    DepartmentScore total = totals.computeIfAbsent(user.getDepartment(),
                            id -> new DepartmentScore(id, departments.get(id)));
                    long points = score(metric, user, collectionScores);
                    // Un apprenti donne ses points a sa filiere sans peser sur son
                    // effectif : la meme comptabilite qu'un defi de promo, des points
                    // sans personne derriere.
                    if (user.isPerf()) {
                        total.addApprentice(points);
                    } else {
                        total.add(points);
                    }
                });
        return totals;
    }

    /**
     * Ce que chaque departement a gagne en relevant des defis de promo.
     *
     * <p>La soumission validee fait foi, bonus compris : un ajustement de points ou
     * une revocation se repercutent d'eux-memes, sans registre a defaire.</p>
     *
     * <p>Un defi ne compte qu'une fois par departement, meme si deux preuves ont
     * ete validees pour lui -- la porte d'envoi l'interdit, ce filet-ci le garantit
     * au classement.</p>
     */
    private Map<UUID, Long> promoAwardsByDepartment() {
        if (challengeSubmissionRepository == null) {
            return Map.of();
        }
        Map<UUID, Map<UUID, Long>> byDepartment = new HashMap<>();
        for (Object[] award : challengeSubmissionRepository.findValidatedPromoAwards()) {
            UUID challengeId = (UUID) award[0];
            UUID departmentId = (UUID) award[1];
            long points = number(award[2]) + number(award[3]);
            byDepartment.computeIfAbsent(departmentId, id -> new HashMap<>())
                    .merge(challengeId, points, Math::max);
        }
        Map<UUID, Long> totals = new HashMap<>();
        byDepartment.forEach((departmentId, byChallenge) ->
                totals.put(departmentId, byChallenge.values().stream().mapToLong(Long::longValue).sum()));
        return totals;
    }

    private static long number(Object value) {
        return value instanceof Number amount ? amount.longValue() : 0L;
    }

    private Map<UUID, Integer> configuredHeadcounts() {
        return departmentSettingsRepository == null ? Map.of() : departmentSettingsRepository.findAll().stream()
                .collect(Collectors.toMap(DepartmentLeaderboardSettings::getDepartmentId, DepartmentLeaderboardSettings::getTheoreticalHeadcount));
    }

    private void requireConfiguredHeadcounts(DepartmentScoringFormula formula) {
        if (formula == DepartmentScoringFormula.RAW_TOTAL) {
            return;
        }
        Map<UUID, DepartmentScore> totals = rawDepartmentScores(null, departmentShortNamesById());
        List<UUID> missing = totals.values().stream().filter(total -> total.memberCount() > 0)
                .map(DepartmentScore::id).filter(id -> !configuredHeadcounts().containsKey(id)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new ConflictException("Effectif theorique manquant pour les departements : " + missing);
        }
    }

    private List<LeaderboardEntry> buildDepartmentEntries(LeaderboardSnapshot snapshot, LeaderboardMetric metric, DepartmentScoringFormula formula) {
        Map<UUID, String> departments = departmentShortNamesById();
        List<DepartmentScore> rankedDepartments = rankedDepartmentScores(metric, formula, departments);

        List<LeaderboardEntry> entries = new ArrayList<>();

        for (int index = 0; index < rankedDepartments.size(); index++) {
            DepartmentScore departmentScore = rankedDepartments.get(index);
            LeaderboardEntry entry = new LeaderboardEntry();

            entry.setSnapshot(snapshot);
            entry.setRank(index + 1);
            entry.setSubjectId(departmentScore.id());
            entry.setName(departmentScore.name());
            entry.setScore(departmentScore.score());
            entry.setRawTotal(departmentScore.rawTotal());
            entry.setMemberCount(departmentScore.memberCount());
            entry.setTheoreticalHeadcount(departmentScore.theoreticalHeadcount());
            entry.setCoefficient(departmentScore.coefficient());
            entry.setPromoPoints(departmentScore.promoPoints());
            entry.setFormula(formula.name());
            entry.setReferenceHeadcount(departmentScore.referenceHeadcount());
            entries.add(entry);
        }

        return entries;
    }

    private UserLeaderboardDto toUserLeaderboardDto(LeaderboardSnapshot snapshot, LeaderboardMetric metric, Set<UUID> hiddenUserIds) {
        return toUserLeaderboardDto(snapshot, metric, null, Set.of(), hiddenUserIds);
    }

    private UserLeaderboardDto toUserLeaderboardDto(
            LeaderboardSnapshot snapshot,
            LeaderboardMetric metric,
            UUID requesterId,
            Set<UUID> friendIds,
            Set<UUID> hiddenUserIds
    ) {
        List<LeaderboardEntry> visibleEntries = entryRepository.findBySnapshotOrderByRankAsc(snapshot).stream()
                .filter(entry -> !hiddenUserIds.contains(entry.getSubjectId()))
                .toList();

        List<RankedRow> rows = groupByTeam(visibleEntries, metric);

        List<UserLeaderboardEntryDto> entryDtos = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            RankedRow row = rows.get(i);
            int rank = i + 1;
            // « Moi et mes amis » vaut pour la ligne qui me contient : dans une
            // equipe, la ligne porte le nom de l'equipe, et c'est celle-la qu'il
            // faut me montrer si elle est hors du top.
            if (requesterId == null
                    || rank <= 10
                    || row.memberIds().contains(requesterId)
                    || row.memberIds().stream().anyMatch(friendIds::contains)) {
                entryDtos.add(new UserLeaderboardEntryDto(
                        rank,
                        row.name(),
                        row.department(),
                        row.score(),
                        requesterId != null && row.memberIds().contains(requesterId),
                        row.memberIds().stream().anyMatch(friendIds::contains),
                        row.memberNames()
                ));
            }
        }

        return new UserLeaderboardDto(
                metric,
                snapshot.getReleasedAt(),
                entryDtos
        );
    }


    /**
     * Une ligne de classement : un joueur seul, ou une equipe.
     *
     * <p>{@code memberIds} porte les joueurs qu'elle represente, pour que « moi et
     * mes amis » continue de designer la bonne ligne une fois les scores mis en
     * commun.</p>
     */
    private record RankedRow(
            UUID id,
            Set<UUID> memberIds,
            String name,
            String department,
            long score,
            List<String> memberNames
    ) {
        /** Une ligne qui ne represente qu'une personne : elle porte deja son nom. */
        private static RankedRow solo(UUID id, String name, String department, long score) {
            return new RankedRow(id, Set.of(id), name, department, score, List.of());
        }
    }

    private static boolean isCollectionMetric(LeaderboardMetric metric) {
        return metric == LeaderboardMetric.PHYSICAL_COLLECTION_POINTS;
    }

    /**
     * Regroupe les lignes de collection par equipe.
     *
     * <p>Le groupement se fait a la lecture et non a la prise du cliche : le cliche
     * garde ce que chaque joueur a marque, ce dont vivent les totaux tous classements
     * confondus et les defis a validation automatique qui lisent un rang. Une equipe
     * y prendrait la place de ses membres, et ni les uns ni les autres n'y
     * retrouveraient leur compte.</p>
     *
     * <p>Un joueur seul dans son equipe reste une ligne a son nom : « en equipe »
     * commence quand quelqu'un vous a rejoint.</p>
     */
    private List<RankedRow> groupByTeam(List<LeaderboardEntry> entries, LeaderboardMetric metric) {
        if (!isCollectionMetric(metric)) {
            return entries.stream()
                    .map(entry -> RankedRow.solo(
                            entry.getSubjectId(),
                            entry.getName(),
                            entry.getDepartment(),
                            entry.getScore() == null ? 0L : entry.getScore()))
                    .toList();
        }

        Map<UUID, CardTeam> teamByUserId = new HashMap<>();
        Map<UUID, Integer> sizeByTeamId = new HashMap<>();
        for (CardTeamMember member : cardTeamMemberRepository.findAllWithTeam()) {
            teamByUserId.put(member.getUser().getId(), member.getTeam());
            sizeByTeamId.merge(member.getTeam().getId(), 1, Integer::sum);
        }

        Map<UUID, TeamRow> teams = new LinkedHashMap<>();
        List<RankedRow> rows = new ArrayList<>();
        for (LeaderboardEntry entry : entries) {
            long score = entry.getScore() == null ? 0L : entry.getScore();
            CardTeam team = teamByUserId.get(entry.getSubjectId());
            if (team == null || sizeByTeamId.getOrDefault(team.getId(), 1) < 2) {
                rows.add(RankedRow.solo(entry.getSubjectId(), entry.getName(), entry.getDepartment(), score));
                continue;
            }
            teams.computeIfAbsent(team.getId(), id -> new TeamRow(
                            id,
                            team.getName(),
                            team.getCreator() == null ? null : team.getCreator().getId()))
                    .add(entry.getSubjectId(), entry.getName(), entry.getDepartment(), score);
        }
        teams.values().forEach(team -> rows.add(team.toRow()));

        return rows.stream()
                .sorted(Comparator
                        .comparingLong(RankedRow::score).reversed()
                        .thenComparing(row -> row.name() == null ? "" : row.name().toLowerCase())
                        .thenComparing(RankedRow::id))
                .toList();
    }

    /** Une equipe en cours d'agregation. */
    private static final class TeamRow {
        private final UUID id;
        private final String name;
        private final UUID creatorId;
        private final Set<UUID> memberIds = new HashSet<>();
        // Les noms suivent l'ordre du classement, celui dans lequel les lignes arrivent :
        // la composition se lit donc du membre qui a le plus marque au moins.
        private final List<String> memberNames = new ArrayList<>();
        private String firstDepartment;
        private String creatorDepartment;
        private boolean departmentShared = true;
        private long score;

        private TeamRow(UUID id, String name, UUID creatorId) {
            this.id = id;
            this.name = name;
            this.creatorId = creatorId;
        }

        private void add(UUID memberId, String memberName, String memberDepartment, long memberScore) {
            memberIds.add(memberId);
            memberNames.add(memberName);
            score += memberScore;
            if (memberIds.size() == 1) {
                firstDepartment = memberDepartment;
            } else if (!Objects.equals(firstDepartment, memberDepartment)) {
                departmentShared = false;
            }
            // Convention arbitraire mais stable pour une equipe mixte : son
            // departement est celui de la personne qui l'a creee.
            if (Objects.equals(memberId, creatorId)) {
                creatorDepartment = memberDepartment;
            }
        }

        private RankedRow toRow() {
            return new RankedRow(
                    id,
                    Set.copyOf(memberIds),
                    name,
                    creatorId == null
                            ? (departmentShared ? firstDepartment : null)
                            : creatorDepartment,
                    score,
                    List.copyOf(memberNames)
            );
        }
    }

    private DepartmentLeaderboardDto toDepartmentLeaderboardDto(LeaderboardSnapshot snapshot, LeaderboardMetric metric) {
        List<LeaderboardEntry> entries = entryRepository.findBySnapshotOrderByRankAsc(snapshot);
        List<DepartmentLeaderboardEntryDto> entryDtos = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry entry = entries.get(i);
            entryDtos.add(new DepartmentLeaderboardEntryDto(
                    i + 1,
                    entry.getName(),
                    entry.getScore(),
                    entry.getRawTotal(),
                    entry.getMemberCount(),
                    entry.getTheoreticalHeadcount(),
                    entry.getCoefficient(),
                    entry.getPromoPoints(),
                    null
            ));
        }

        // Toutes les entrees d'un classement portent la meme formule : la premiere suffit.
        String formula = entries.stream().map(LeaderboardEntry::getFormula)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        Integer referenceHeadcount = entries.stream().map(LeaderboardEntry::getReferenceHeadcount)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        Optional<LeaderboardConfig> totalConfig = totalConfig(metric);
        return new DepartmentLeaderboardDto(
                metric,
                snapshot.getReleasedAt(),
                formula,
                referenceHeadcount,
                totalConfig.map(LeaderboardConfig::getTotalWeightPercent).orElse(null),
                totalConfig.map(LeaderboardConfig::getTotalScaleFactor).orElse(null),
                entryDtos
        );
    }

    /**
     * Le poids et le facteur d'une metrique dans le classement total.
     *
     * <p>Ils vivent sur la configuration joueur : les departements prennent les memes, pour
     * que leur total se calcule comme celui des joueurs. Une metrique sans configuration
     * joueur n'en a pas, et le dire vaut mieux que faire tomber tout le classement.</p>
     */
    private Optional<LeaderboardConfig> totalConfig(LeaderboardMetric metric) {
        return configRepository.findByScopeAndMetric(LeaderboardScope.USER, metric);
    }

    private LeaderboardSnapshot getLatestSnapshot(LeaderboardScope scope, LeaderboardMetric metric) {
        LeaderboardConfig config = getConfig(scope, metric);

        return getLatestSnapshot(config);
    }

    private LeaderboardSnapshot getLatestSnapshot(LeaderboardConfig config) {
        return snapshotRepository.findFirstByConfigOrderByReleasedAtDesc(config)
                .orElseGet(() -> createZeroSnapshot(config));
    }

    /**
     * Rend le dernier instantane calcule avec la population encore eligible.
     *
     * <p>Une ligne departement ne garde que le total : une fois ecrite, on ne peut pas
     * lui soustraire exactement la part d'un joueur devenu ineligible. Le marqueur de
     * population permet donc de publier un nouvel instantane par le chemin normal de
     * release, sans modifier l'ancien ni rafraichir les scores quand seule leur valeur a
     * change.</p>
     */
    private LeaderboardSnapshot getCurrentDepartmentSnapshot(LeaderboardMetric metric) {
        LeaderboardConfig config = getConfig(LeaderboardScope.DEPARTMENT, metric);
        LeaderboardSnapshot latestSnapshot = getLatestSnapshot(config);
        String currentFingerprint = currentEligibilityFingerprint();

        if (currentFingerprint == null
                || currentFingerprint.equals(latestSnapshot.getEligibilityFingerprint())) {
            return latestSnapshot;
        }

        return release(config);
    }

    /**
     * Empreinte des joueurs exclus par une classification Keycloak connue.
     *
     * <p>Si l'annuaire est indisponible, aucune empreinte n'est avancee : cela conserve
     * le repli permissif existant et un instantane sans marqueur sera recontrole lorsque
     * l'annuaire repondra de nouveau.</p>
     */
    private String currentEligibilityFingerprint() {
        if (roleDirectory == null || !roleDirectory.isKnown()) {
            return null;
        }

        String excludedUserIds = userRepository.findAll().stream()
                .filter(user -> !rankable(user))
                .map(AppUser::getId)
                .filter(Objects::nonNull)
                .map(UUID::toString)
                .sorted()
                .collect(Collectors.joining(","));
        String departmentScoringState = configRepository.findAll().stream()
                .filter(config -> config.getScope() == LeaderboardScope.DEPARTMENT)
                .sorted(Comparator.comparing(LeaderboardConfig::getMetric))
                .map(config -> config.getMetric() + ":" + config.getFormula())
                .collect(Collectors.joining(",")) + "\n" + configuredHeadcounts().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(","));

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (ELIGIBILITY_POLICY_VERSION + "\n" + excludedUserIds + "\n" + departmentScoringState)
                            .getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponible.", exception);
        }
    }

    private LeaderboardSnapshot createZeroSnapshot(LeaderboardConfig config) {
        LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
        Instant releasedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();

        snapshot.setConfig(config);
        snapshot.setReleasedAt(releasedAt);
        if (config.getScope() == LeaderboardScope.DEPARTMENT) {
            snapshot.setEligibilityFingerprint(currentEligibilityFingerprint());
        }
        LeaderboardSnapshot savedSnapshot = snapshotRepository.save(snapshot);

        // Le classement a zero est bati par les memes fonctions que le vrai : la
        // metrique nulle vaut zero pour tout le monde, et la population reste la
        // meme. Lister ici tous les departements de la base donnait un tableau
        // departements ou GM apparaissait a 0 pendant que le tableau joueurs, lui,
        // etait vide : deux ecrans qui se contredisent alors qu'aucun joueur
        // n'existe encore.
        //
        // Sauf pour un classement qui ne montre que les joueurs classes (l'Elo) : zero n'y est pas
        // un score, c'est l'absence de classement. Y lister tout le monde a zero affichait un
        // classement Elo rempli de zeros pendant que les vrais joueurs etaient a 1000 et plus.
        boolean onlyScoredPlayers = config.getMetric() != null && config.getMetric().ranksOnlyScoredPlayers();
        List<LeaderboardEntry> entries = switch (config.getScope()) {
            case USER -> onlyScoredPlayers ? List.of() : buildUserEntries(savedSnapshot, null);
            case DEPARTMENT -> buildDepartmentEntries(savedSnapshot, null, config.getFormula());
        };

        entryRepository.saveAll(entries);
        config.setLastReleasedAt(releasedAt);
        configRepository.save(config);

        return savedSnapshot;
    }

    private LeaderboardConfig getConfig(LeaderboardScope scope, LeaderboardMetric metric) {
        return configRepository.findByScopeAndMetric(scope, metric)
                .orElseThrow(() -> new NotFoundException(
                        "Configuration de classement introuvable pour " + scope + " / " + metric + "."
                ));
    }

    /**
     * Les classements demandes, ou a defaut tous ceux qui existent a cette echelle.
     *
     * <p>L'echelle filtre la liste, par defaut comme explicite : tous les classements ne se
     * calculent pas a toutes les echelles, et parcourir {@code values()} sans ce filtre fait
     * lever une introuvable a la premiere metrique sans configuration — c'est-a-dire tomber le
     * classement des departements en entier pour le classement Elo, qui ne le concerne pas.</p>
     */
    private List<LeaderboardMetric> normalizeMetrics(
            LeaderboardScope scope, List<LeaderboardMetric> requestedMetrics) {
        if (requestedMetrics == null || requestedMetrics.isEmpty()) {
            return Arrays.stream(LeaderboardMetric.values())
                    .filter(metric -> metric.supports(scope))
                    .toList();
        }

        // Une liste explicite est filtree de meme. Ces routes rendent tous les classements
        // demandes d'un coup : un seul classement inexistant a cette echelle ferait echouer la
        // reponse entiere, et le site, qui avale l'erreur, afficherait alors zero classement de
        // departement au lieu de tous sauf un.
        return requestedMetrics.stream()
                .filter(java.util.Objects::nonNull)
                .filter(metric -> metric.supports(scope))
                .distinct()
                .toList();
    }

    /**
     * Qui a sa place au classement.
     *
     * <p>Le role etudiant, et pas de role de staff. « Pas de role du tout » ne suffisait
     * pas : un compte cree mais jamais valide n'a rien fait, et un respo qui joue en marge
     * n'a pas a figurer parmi ceux qui concourent.</p>
     *
     * <p>La regle ne s'applique que si Keycloak a repondu. Sans cette reserve, une panne --
     * ou le defi Cloudflare qui a bloque l'annuaire en production -- viderait le classement
     * au lieu de le laisser tel quel. Ne pas savoir n'est pas savoir que non.</p>
     */
    /**
     * Vrai quand ce joueur a sa place dans les classements publics.
     *
     * <p>Publique pour que les listes de l'administration designent les memes personnes que les
     * classements, sans reecrire la regle : elle depend des roles Keycloak, et une seconde copie
     * divergerait au premier changement de role.</p>
     */
    public boolean isRankable(AppUser user) {
        return user != null && rankable(user);
    }

    private boolean rankable(AppUser user) {
        if (Boolean.TRUE.equals(user.getHiddenFromLeaderboards())) {
            return false;
        }
        if (roleDirectory == null || !roleDirectory.isKnown()) {
            return true;
        }
        String sub = user.getKeycloakSub();
        return sub != null
                && roleDirectory.students().contains(sub)
                && !roleDirectory.teamStaff().contains(sub);
    }

    /**
     * Ceux qu'un instantane deja calcule ne doit plus montrer.
     *
     * <p>Les instantanes sont ecrits a l'avance : exclure a la construction ne corrige que
     * les suivants. Cette liste-ci s'applique a la lecture, donc tout de suite.</p>
     */
    private Set<UUID> hiddenUserIds() {
        return userRepository.findAll().stream()
                .filter(user -> !rankable(user))
                .map(AppUser::getId)
                .collect(Collectors.toSet());
    }

    private AdminLeaderboardConfigDto toConfigDto(LeaderboardConfig config) {
        return new AdminLeaderboardConfigDto(
                config.getScope(),
                config.getMetric(),
                config.getRefreshDelayMinutes(),
                config.getAutoRefreshEnabled(),
                config.getLastReleasedAt(),
                config.getFormula(),
                config.getTotalWeightPercent(),
                config.getTotalScaleFactor()
        );
    }

    private Map<UUID, String> departmentShortNamesById() {
        Map<UUID, String> departments = new HashMap<>();

        for (Department department : departmentRepository.findAllByVirtualFalse()) {
            departments.put(department.getId(), department.getShortName());
        }

        return departments;
    }

    private String displayName(AppUser user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }

        return user.getEmail();
    }

    private String normalizedDisplayName(AppUser user) {
        return displayName(user).toLowerCase();
    }

    private long score(LeaderboardMetric metric, AppUser user, Map<UUID, Long> precomputedScores) {
        if (metric == null) {
            return 0L;
        }
        // Collection et jeu ne se lisent pas sur le joueur : l'un se recompte
        // depuis ses cartes, l'autre depuis ses parties. Les deux arrivent ici
        // déjà calculés pour tout le monde, en une requête.
        if (metric == LeaderboardMetric.PHYSICAL_COLLECTION_POINTS
                || metric == LeaderboardMetric.VIRTUAL_COLLECTION_POINTS
                || metric == LeaderboardMetric.GAME_POINTS
                || metric == LeaderboardMetric.PVP_ELO) {
            return precomputedScores.getOrDefault(user.getId(), 0L);
        }
        return metric.score(user);
    }

    private Map<UUID, Long> collectionScores(LeaderboardMetric metric) {
        if (metric == null) {
            return Map.of();
        }
        if (metric == LeaderboardMetric.GAME_POINTS) {
            return gameScores();
        }
        if (metric == LeaderboardMetric.PVP_ELO) {
            return pvpEloScores();
        }
        CardCollectionType collectionType = switch (metric) {
            case PHYSICAL_COLLECTION_POINTS -> CardCollectionType.PHYSICAL;
            case VIRTUAL_COLLECTION_POINTS -> CardCollectionType.VIRTUAL;
            default -> null;
        };
        if (collectionType == null) {
            return Map.of();
        }

        // Read the settings once for the whole snapshot rather than per row.
        CardCollectionSettings settings = collectionPointsCalculator.settings();
        Map<UUID, Long> scores = new HashMap<>();
        for (CardCollection collection : cardCollectionRepository.findByCollectionTypeForScoring(collectionType)) {
            scores.merge(
                    collection.getUser().getId(),
                    collectionPointsCalculator.points(collection, settings),
                    Long::sum
            );
        }
        return scores;
    }

    /**
     * Ce que le jeu a rapporté à chacun.
     *
     * <p>La somme des jetons versés en fin de partie — le gain réel, celui qui
     * arrive sur le solde —, et non le score interne dont ils sont tirés. Somme et
     * non meilleure partie : le classement dit « les points obtenus », comme les
     * trois autres, et jouer beaucoup compte autant que jouer bien.</p>
     */
    private Map<UUID, Long> gameScores() {
        Map<UUID, Long> scores = new HashMap<>();
        for (Object[] row : stsRunRepository.sumRetirementTokensByOwner()) {
            scores.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return scores;
    }

    /**
     * Le classement Elo de chacun, pour ceux qui ont joue.
     *
     * <p>Un joueur sans duel classe n'a pas d'entree ici et se classe donc a zero, derriere tous
     * ceux qui ont joue. Il ne s'agit pas d'un oubli : son profil existe peut-etre deja a 1000 —
     * ouvrir le menu multijoueur suffit a le creer — et ce chiffre-la ne mesure rien.</p>
     */
    private Map<UUID, Long> pvpEloScores() {
        Map<UUID, Long> scores = new HashMap<>();
        for (Object[] row : pvpProfileRepository.findRankedElos()) {
            scores.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return scores;
    }

    private static final class DepartmentScore {
        private final UUID id;
        private final String name;
        private long score;
        private int memberCount;
        // Combien d'apprentis ont donne leurs points sans peser sur l'effectif.
        private int perfCount;
        // Le detail d'un score classe ; vide sur un total brut, avant formule.
        private long rawTotal;
        private Integer theoreticalHeadcount;
        private Double coefficient;
        private long promoPoints;
        private Integer referenceHeadcount;

        private DepartmentScore(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        private DepartmentScore(UUID id, String name, long score, int memberCount) {
            this.id = id;
            this.name = name;
            this.score = score;
            this.memberCount = memberCount;
        }

        private UUID id() {
            return id;
        }

        private String name() {
            return name;
        }

        private long score() {
            return score;
        }

        private int memberCount() { return memberCount; }

        private DepartmentScore(UUID id, String name, long score, int memberCount, int perfCount, long rawTotal,
                                Integer theoreticalHeadcount, Double coefficient, long promoPoints,
                                Integer referenceHeadcount) {
            this(id, name, score, memberCount);
            this.perfCount = perfCount;
            this.rawTotal = rawTotal;
            this.theoreticalHeadcount = theoreticalHeadcount;
            this.coefficient = coefficient;
            this.promoPoints = promoPoints;
            this.referenceHeadcount = referenceHeadcount;
        }

        private long rawTotal() { return rawTotal; }
        private Integer theoreticalHeadcount() { return theoreticalHeadcount; }
        private Double coefficient() { return coefficient; }
        private long promoPoints() { return promoPoints; }
        private Integer referenceHeadcount() { return referenceHeadcount; }

        private void add(long score) {
            this.score += score;
            this.memberCount++;
        }

        private void addApprentice(long score) {
            this.score += score;
            this.perfCount++;
        }

        private int perfCount() { return perfCount; }

        /** Des points sans personne derriere : un defi de promo n'est pas un membre. */
        private void addPoints(long points) {
            this.score += points;
        }
    }

    private static final class TotalScore {
        private final UUID id;
        private final String name;
        private final String department;
        private long score;

        private TotalScore(UUID id, String name, String department) {
            this.id = id;
            this.name = name;
            this.department = department;
        }

        private UUID id() {
            return id;
        }

        private String name() {
            return name;
        }

        private String department() {
            return department;
        }

        private long score() {
            return score;
        }

        private void add(long value) {
            score += value;
        }
    }
}
