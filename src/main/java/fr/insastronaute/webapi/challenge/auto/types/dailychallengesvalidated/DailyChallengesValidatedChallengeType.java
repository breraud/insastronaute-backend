package fr.insastronaute.webapi.challenge.auto.types.dailychallengesvalidated;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidatePage;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameter;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeProgress;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeType;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * « Fais valider X défis du jour », depuis toujours.
 *
 * <p>Un compteur plutôt qu'un bilan : il ne fait que monter, donc il se tranche
 * dès que X est atteint, sans attendre de connaître tous les défis du jour d'une
 * période.</p>
 */
@Component
public final class DailyChallengesValidatedChallengeType implements AutoChallengeType<DailyChallengesValidatedParameters> {

    private final ChallengeSubmissionRepository submissionRepository;

    public DailyChallengesValidatedChallengeType(ChallengeSubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    @Override
    public String key() {
        return "DAILY_CHALLENGES_VALIDATED";
    }

    @Override
    public String publicName() {
        return "Valider des défis du jour";
    }

    @Override
    public String description() {
        return "Se déclenche quand le joueur a fait valider un nombre donné de défis du jour distincts, seul ou en groupe.";
    }

    @Override
    public Class<DailyChallengesValidatedParameters> parametersType() {
        return DailyChallengesValidatedParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of(AutoChallengeParameter.integer("count", "Nombre de défis du jour validés", 1, 100_000));
    }

    @Override
    public String phrase(DailyChallengesValidatedParameters parameters) {
        return parameters.count() == 1
                ? "Fais valider un défi du jour"
                : "Fais valider {n} défis du jour".replace("{n}", String.valueOf(parameters.count()));
    }

    @Override
    public Set<AutoChallengeTrigger> triggers() {
        return Set.of(AutoChallengeTrigger.CHALLENGE_VALIDATED);
    }

    @Override
    public AutoChallengeProgress progress(DailyChallengesValidatedParameters parameters, UUID userId) {
        return AutoChallengeProgress.of(submissionRepository.countValidatedDailyChallengesForUser(userId), parameters.count());
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(DailyChallengesValidatedParameters parameters, AutoChallengeCandidateQuery query) {
        List<UUID> userIds = submissionRepository.findUsersWithValidatedDailyChallenges(
                parameters.count(), query.challengeId(), query.afterUserId(), PageRequest.of(0, query.limit()));
        return new AutoChallengeCandidatePage(userIds, userIds.size() < query.limit() ? null : userIds.getLast());
    }
}
