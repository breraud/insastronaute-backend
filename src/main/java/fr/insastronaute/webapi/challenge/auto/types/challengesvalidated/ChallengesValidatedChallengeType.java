package fr.insastronaute.webapi.challenge.auto.types.challengesvalidated;

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

@Component
public final class ChallengesValidatedChallengeType implements AutoChallengeType<ChallengesValidatedParameters> {

    private final ChallengeSubmissionRepository submissionRepository;

    public ChallengesValidatedChallengeType(ChallengeSubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    @Override
    public String key() {
        return "CHALLENGES_VALIDATED";
    }

    @Override
    public String publicName() {
        return "Valider des défis";
    }

    @Override
    public String description() {
        return "Se déclenche quand le joueur a fait valider un nombre donné de défis distincts, seul ou en groupe.";
    }

    @Override
    public Class<ChallengesValidatedParameters> parametersType() {
        return ChallengesValidatedParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of(AutoChallengeParameter.integer("count", "Nombre de défis validés", 1, 100_000));
    }

    @Override
    public String phrase(ChallengesValidatedParameters parameters) {
        return parameters.count() == 1
                ? "Fais valider un défi"
                : "Fais valider {n} défis".replace("{n}", String.valueOf(parameters.count()));
    }

    @Override
    public Set<AutoChallengeTrigger> triggers() {
        return Set.of(AutoChallengeTrigger.CHALLENGE_VALIDATED);
    }

    @Override
    public AutoChallengeProgress progress(ChallengesValidatedParameters parameters, UUID userId) {
        return AutoChallengeProgress.of(submissionRepository.countValidatedChallengesForUser(userId), parameters.count());
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(ChallengesValidatedParameters parameters, AutoChallengeCandidateQuery query) {
        List<UUID> userIds = submissionRepository.findUsersWithValidatedChallenges(parameters.count(), query.challengeId(), query.afterUserId(), PageRequest.of(0, query.limit()));
        return new AutoChallengeCandidatePage(userIds, userIds.size() < query.limit() ? null : userIds.getLast());
    }
}
