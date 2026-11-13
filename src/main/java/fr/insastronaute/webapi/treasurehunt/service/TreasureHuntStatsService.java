package fr.insastronaute.webapi.treasurehunt.service;

import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.leaderboard.service.LeaderboardService;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntCheckpoint;
import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntPath;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntCheckpointPlayerResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntCheckpointPlayersResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntCheckpointStatsResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntPathStatsResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntPlayerPathStatsResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntPlayerStatsResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntStepStatsResponse;
import fr.insastronaute.webapi.treasurehunt.dto.TreasureHuntValidatedStepResponse;
import fr.insastronaute.webapi.treasurehunt.repository.PlayerCheckpointProgressRepository;
import fr.insastronaute.webapi.treasurehunt.repository.PlayerVaultCodeRepository;
import fr.insastronaute.webapi.treasurehunt.repository.PlayerVaultUnlockRepository;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntCheckpointRepository;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntPathRepository;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntValidation;
import fr.insastronaute.webapi.treasurehunt.repository.TreasureHuntVaultCodeIssue;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Les statistiques de la chasse, lues depuis les seules reussites.
 *
 * Tout est agrege ici plutot qu'en SQL : la chasse compte six voies d'une poignee
 * d'etapes, et quelques milliers de validations tiennent sans peine en memoire. La
 * mediane, elle, n'a pas d'equivalent portable entre PostgreSQL et la base H2 des
 * tests.
 *
 * <p>Les chiffres agreges ne comptent que les joueurs des classements publics, selon
 * leur regle et non une copie : le role etudiant, aucun role de staff, pas masque du
 * classement -- et tout le monde quand Keycloak ne repond pas. Un respo qui teste la
 * chasse avant l'ouverture ne doit ni gonfler les participants ni ecraser la mediane
 * avec ses soixante secondes par etape. La fiche d'un joueur, elle, montre toujours
 * sa propre progression, staff compris : elle ne compte que lui.</p>
 */
@Service
public class TreasureHuntStatsService {
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final TreasureHuntPathRepository pathRepository;
    private final TreasureHuntCheckpointRepository checkpointRepository;
    private final PlayerCheckpointProgressRepository progressRepository;
    private final PlayerVaultCodeRepository vaultCodeRepository;
    private final PlayerVaultUnlockRepository vaultUnlockRepository;
    private final LeaderboardService leaderboardService;

    public TreasureHuntStatsService(UserRepository userRepository, DepartmentRepository departmentRepository,
            TreasureHuntPathRepository pathRepository, TreasureHuntCheckpointRepository checkpointRepository,
            PlayerCheckpointProgressRepository progressRepository, PlayerVaultCodeRepository vaultCodeRepository,
            PlayerVaultUnlockRepository vaultUnlockRepository, LeaderboardService leaderboardService) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.pathRepository = pathRepository;
        this.checkpointRepository = checkpointRepository;
        this.progressRepository = progressRepository;
        this.vaultCodeRepository = vaultCodeRepository;
        this.vaultUnlockRepository = vaultUnlockRepository;
        this.leaderboardService = leaderboardService;
    }

    @Transactional(readOnly = true)
    public TreasureHuntCheckpointStatsResponse checkpointStats() {
        List<TreasureHuntValidation> allValidations = progressRepository.findAllValidations();
        List<TreasureHuntVaultCodeIssue> allIssues = vaultCodeRepository.findAllIssues();
        List<UUID> allUnlocks = vaultUnlockRepository.findAllUserIds();
        Set<UUID> counted = rankedAmong(Stream.of(
                        allValidations.stream().map(TreasureHuntValidation::userId),
                        allIssues.stream().map(TreasureHuntVaultCodeIssue::userId),
                        allUnlocks.stream())
                .flatMap(Function.identity())
                .toList());

        List<TreasureHuntValidation> validations = allValidations.stream()
                .filter(validation -> counted.contains(validation.userId()))
                .toList();
        Map<UUID, List<TreasureHuntValidation>> validationsByCheckpoint = validations.stream()
                .collect(Collectors.groupingBy(TreasureHuntValidation::checkpointId));
        Map<UUID, Long> startedByPath = validations.stream()
                .collect(Collectors.groupingBy(TreasureHuntValidation::pathId,
                        Collectors.mapping(TreasureHuntValidation::userId,
                                Collectors.collectingAndThen(Collectors.toSet(), users -> (long) users.size()))));
        Map<UUID, Long> completedByPath = allIssues.stream()
                .filter(issue -> counted.contains(issue.userId()))
                .collect(Collectors.groupingBy(TreasureHuntVaultCodeIssue::pathId, Collectors.counting()));

        List<TreasureHuntPathStatsResponse> paths = pathRepository.findAllByOrderByPositionAsc().stream()
                .map(path -> new TreasureHuntPathStatsResponse(
                        path.getId(),
                        path.getPosition(),
                        path.getName(),
                        path.getDepartment().getShortName(),
                        startedByPath.getOrDefault(path.getId(), 0L),
                        completedByPath.getOrDefault(path.getId(), 0L),
                        stepStats(path, validationsByCheckpoint)))
                .toList();
        long participantCount = validations.stream().map(TreasureHuntValidation::userId).distinct().count();
        long vaultUnlockCount = allUnlocks.stream().filter(counted::contains).count();
        return new TreasureHuntCheckpointStatsResponse(participantCount, vaultUnlockCount, paths);
    }

    /**
     * Qui est passe par une etape, dans l'ordre d'arrivee, staff exclu comme dans les
     * chiffres qu'elle detaille : la liste doit tomber sur le compte de la ligne.
     */
    @Transactional(readOnly = true)
    public TreasureHuntCheckpointPlayersResponse checkpointPlayers(UUID checkpointId) {
        TreasureHuntCheckpoint checkpoint = checkpointRepository.findById(checkpointId)
                .orElseThrow(() -> new NotFoundException("Étape introuvable."));
        List<TreasureHuntCheckpoint> steps = checkpoint.getPath().getCheckpoints();
        int index = steps.indexOf(checkpoint);
        TreasureHuntCheckpoint previousStep = index > 0 ? steps.get(index - 1) : null;

        List<TreasureHuntValidation> validations = progressRepository.findValidationsByCheckpointIds(
                previousStep == null ? List.of(checkpointId) : List.of(checkpointId, previousStep.getId()));
        List<TreasureHuntValidation> here = validations.stream()
                .filter(validation -> validation.checkpointId().equals(checkpointId))
                .toList();
        Map<UUID, Instant> previous = previousStep == null ? Map.of() : validations.stream()
                .filter(validation -> validation.checkpointId().equals(previousStep.getId()))
                .collect(Collectors.toMap(TreasureHuntValidation::userId, TreasureHuntValidation::validatedAt));

        Map<UUID, AppUser> players = userRepository.findAllById(
                        here.stream().map(TreasureHuntValidation::userId).collect(Collectors.toSet()))
                .stream()
                .filter(leaderboardService::isRankable)
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        Map<UUID, String> departmentShortNames = departmentRepository.findAllById(players.values().stream()
                        .map(AppUser::getPersistedDepartment)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Department::getId, Department::getShortName));

        List<TreasureHuntCheckpointPlayerResponse> arrivals = here.stream()
                .filter(validation -> players.containsKey(validation.userId()))
                .sorted(Comparator.comparing(TreasureHuntValidation::validatedAt))
                .map(validation -> {
                    AppUser player = players.get(validation.userId());
                    Instant before = previous.get(validation.userId());
                    return new TreasureHuntCheckpointPlayerResponse(
                            player.getId(),
                            player.getDisplayName(),
                            player.getPersistedDepartment() == null ? null
                                    : departmentShortNames.get(player.getPersistedDepartment()),
                            validation.validatedAt(),
                            before == null ? null : Duration.between(before, validation.validatedAt()).toSeconds());
                })
                .toList();
        return new TreasureHuntCheckpointPlayersResponse(checkpointId, checkpoint.getPath().getId(),
                checkpoint.getStepNumber(), arrivals);
    }

    @Transactional(readOnly = true)
    public TreasureHuntPlayerStatsResponse playerStats(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Utilisateur introuvable.");
        }
        List<TreasureHuntValidation> validations = progressRepository.findValidationsByUserId(userId);
        Map<UUID, List<TreasureHuntValidation>> validationsByPath = validations.stream()
                .collect(Collectors.groupingBy(TreasureHuntValidation::pathId));
        Map<UUID, Instant> vaultCodeIssuedAtByPath = vaultCodeRepository.findIssuesByUserId(userId).stream()
                .collect(Collectors.toMap(TreasureHuntVaultCodeIssue::pathId, TreasureHuntVaultCodeIssue::issuedAt));

        List<TreasureHuntPlayerPathStatsResponse> paths = pathRepository.findAllByOrderByPositionAsc().stream()
                .map(path -> {
                    List<TreasureHuntValidatedStepResponse> steps = validationsByPath.getOrDefault(path.getId(), List.of())
                            .stream()
                            .sorted(Comparator.comparing(TreasureHuntValidation::stepNumber))
                            .map(validation -> new TreasureHuntValidatedStepResponse(
                                    validation.stepNumber(), validation.validatedAt()))
                            .toList();
                    return new TreasureHuntPlayerPathStatsResponse(
                            path.getId(),
                            path.getPosition(),
                            path.getName(),
                            path.getDepartment().getShortName(),
                            path.getCheckpoints().size(),
                            steps.size(),
                            vaultCodeIssuedAtByPath.get(path.getId()),
                            steps);
                })
                .toList();
        Instant firstValidatedAt = validations.stream()
                .map(TreasureHuntValidation::validatedAt)
                .min(Comparator.naturalOrder())
                .orElse(null);
        return new TreasureHuntPlayerStatsResponse(userId, firstValidatedAt,
                vaultUnlockRepository.findUnlockedAtByUserId(userId).orElse(null), paths);
    }

    /** Parmi ces joueurs, ceux que les classements publics comptent. */
    private Set<UUID> rankedAmong(Collection<UUID> userIds) {
        return userRepository.findAllById(new HashSet<>(userIds)).stream()
                .filter(leaderboardService::isRankable)
                .map(AppUser::getId)
                .collect(Collectors.toSet());
    }

    /**
     * Le delai se mesure depuis l'etape qui precede dans la voie telle qu'elle est
     * ordonnee aujourd'hui, et seulement pour les joueurs qui ont valide les deux.
     */
    private static List<TreasureHuntStepStatsResponse> stepStats(TreasureHuntPath path,
            Map<UUID, List<TreasureHuntValidation>> validationsByCheckpoint) {
        List<TreasureHuntCheckpoint> checkpoints = path.getCheckpoints();
        return IntStream.range(0, checkpoints.size())
                .mapToObj(index -> {
                    TreasureHuntCheckpoint checkpoint = checkpoints.get(index);
                    List<TreasureHuntValidation> here = validationsByCheckpoint.getOrDefault(checkpoint.getId(), List.of());
                    Map<UUID, Instant> previous = index == 0 ? Map.of()
                            : validationsByCheckpoint.getOrDefault(checkpoints.get(index - 1).getId(), List.of()).stream()
                                    .collect(Collectors.toMap(TreasureHuntValidation::userId, TreasureHuntValidation::validatedAt));
                    List<Long> delays = here.stream()
                            .filter(validation -> previous.containsKey(validation.userId()))
                            .map(validation -> Duration.between(previous.get(validation.userId()), validation.validatedAt()).toSeconds())
                            .sorted()
                            .toList();
                    return new TreasureHuntStepStatsResponse(
                            checkpoint.getId(),
                            checkpoint.getStepNumber(),
                            here.size(),
                            here.stream().map(TreasureHuntValidation::validatedAt).min(Comparator.naturalOrder()).orElse(null),
                            here.stream().map(TreasureHuntValidation::validatedAt).max(Comparator.naturalOrder()).orElse(null),
                            median(delays));
                })
                .toList();
    }

    private static Long median(List<Long> sorted) {
        if (sorted.isEmpty()) {
            return null;
        }
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }
}
