package fr.insastronaute.webapi.challenge.auto.award;

import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.cardteam.repository.CardTeamMemberRepository;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.challenge.repository.ChallengeRepository;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import fr.insastronaute.webapi.challenge.service.ChallengeService;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AutoChallengeAwardService {

    private final ChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final ChallengeSubmissionRepository submissionRepository;
    private final CardTeamMemberRepository teamMemberRepository;
    private final ChallengeService challengeService;
    private final AutoChallengeAwardAttemptService attemptService;

    public AutoChallengeAwardService(
            ChallengeRepository challengeRepository,
            UserRepository userRepository,
            ChallengeSubmissionRepository submissionRepository,
            ChallengeService challengeService,
            AutoChallengeAwardAttemptService attemptService,
            CardTeamMemberRepository teamMemberRepository
    ) {
        this.challengeRepository = challengeRepository;
        this.userRepository = userRepository;
        this.submissionRepository = submissionRepository;
        this.challengeService = challengeService;
        this.attemptService = attemptService;
        this.teamMemberRepository = teamMemberRepository;
    }

    /**
     * Le seul portail des attributions automatiques.
     *
     * <p>Décidé sur la vraie horloge. Le chemin chaud et le rattrapage à
     * l'enregistrement tournent sur le fil de la requête, qui peut porter le voyage
     * dans le temps d'un membre du staff : sous lui, un défi du mois prochain
     * passait pour ouvert, et la soumission VALIDATED qui en sortait, elle, était
     * bien réelle.</p>
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public int awardBatch(UUID challengeId, List<UUID> candidateIds) {
        return TimeTravelContext.withRealTime(() -> awardBatchOnRealClock(challengeId, candidateIds));
    }

    private int awardBatchOnRealClock(UUID challengeId, List<UUID> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return 0;
        }

        Challenge challenge = challengeRepository.findById(challengeId).orElse(null);
        if (challenge == null || !challengeService.isOpenForAutomaticAward(challenge)) {
            return 0;
        }

        List<UUID> requestedIds = candidateIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new), List::copyOf
                ));
        if (requestedIds.isEmpty()) {
            return 0;
        }

        Map<UUID, AppUser> usersById = new LinkedHashMap<>();
        userRepository.findAllById(requestedIds).forEach(user -> usersById.put(user.getId(), user));
        Set<UUID> blockedUserIds = blockingUserIds(
                submissionRepository.findBlockingSubmissionsForUsersAndChallenge(requestedIds, challengeId)
        );

        List<List<UUID>> groups = challenge.isRewardShared()
                ? groupByCardTeam(requestedIds)
                : requestedIds.stream().map(List::of).toList();

        int awarded = 0;
        for (List<UUID> group : groups) {
            // Un groupe dont un seul membre a deja une soumission a deja touche : le
            // reattribuer donnerait a l'equipe deux fois la mise pour un accomplissement.
            if (group.stream().anyMatch(blockedUserIds::contains)) {
                continue;
            }

            List<UUID> eligible = group.stream()
                    .filter(id -> {
                        AppUser user = usersById.get(id);
                        return user != null && challengeService.isPlayerEligibleForChallenge(user, challenge);
                    })
                    .toList();
            if (eligible.isEmpty()) {
                continue;
            }

            try {
                attemptService.award(
                        challenge.getId(),
                        eligible.get(0),
                        eligible.subList(1, eligible.size()));
                awarded += eligible.size();
            } catch (DataIntegrityViolationException exception) {
                if (!isExpectedCollision(exception)) {
                    throw exception;
                }
            }
        }
        return awarded;
    }

    /**
     * Regrouper les candidats par equipe de cartes.
     *
     * <p>La collection physique est mutualisee par equipe : l'objectif est atteint
     * collectivement, donc la recompense se compte une fois par equipe et non une fois par
     * joueur. Seuls les membres presents parmi les candidats entrent dans le groupe --
     * ceux qui n'ont pas atteint l'objectif n'ont rien a partager.</p>
     *
     * <p>L'ordre des candidats est conserve, si bien que le premier de chaque groupe --
     * celui qui portera la soumission -- ne depend pas de l'ordre de la base.</p>
     */
    private List<List<UUID>> groupByCardTeam(List<UUID> candidateIds) {
        Map<UUID, List<UUID>> byTeam = new LinkedHashMap<>();
        List<List<UUID>> alone = new java.util.ArrayList<>();

        for (UUID candidateId : candidateIds) {
            UUID teamId = teamMemberRepository.findByUserId(candidateId)
                    .map(member -> member.getTeam().getId())
                    .orElse(null);
            if (teamId == null) {
                // Sans equipe, il n'y a personne avec qui partager : la mise entiere.
                alone.add(List.of(candidateId));
                continue;
            }
            byTeam.computeIfAbsent(teamId, key -> new java.util.ArrayList<>()).add(candidateId);
        }

        List<List<UUID>> groups = new java.util.ArrayList<>(byTeam.values());
        groups.addAll(alone);
        return groups;
    }

    private boolean isExpectedCollision(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                return "challenge_active_memberships_pkey".equals(name)
                        || "challenge_completion_claims_pkey".equals(name);
            }
            String message = cause.getMessage();
            if (message != null && (message.contains("challenge_active_memberships_pkey")
                    || message.contains("challenge_completion_claims_pkey"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private Set<UUID> blockingUserIds(List<ChallengeSubmission> submissions) {
        Set<UUID> blockedIds = new LinkedHashSet<>();
        for (ChallengeSubmission submission : submissions) {
            if (submission.getUser() != null) {
                blockedIds.add(submission.getUser().getId());
            }
            submission.getParticipants().stream().map(AppUser::getId).forEach(blockedIds::add);
        }
        return blockedIds;
    }

}
