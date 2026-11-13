package fr.insastronaute.webapi.challenge.auto.types.stsbestscore;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidatePage;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameter;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeProgress;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeType;
import fr.insastronaute.webapi.sts.repository.StsRunRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public final class StsBestScoreChallengeType implements AutoChallengeType<StsBestScoreParameters> {

    private final StsRunRepository runRepository;

    public StsBestScoreChallengeType(StsRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @Override
    public String key() {
        return "STS_BEST_SCORE";
    }

    @Override
    public String publicName() {
        return "Atteindre un score en Aventure";
    }

    @Override
    public String description() {
        return "Se déclenche quand le joueur a pris sa retraite avec au moins ce score sur une run.";
    }

    @Override
    public Class<StsBestScoreParameters> parametersType() {
        return StsBestScoreParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of(AutoChallengeParameter.integer("score", "Score à atteindre", 1, 10_000_000));
    }

    @Override
    public String phrase(StsBestScoreParameters parameters) {
        return "Termine une run d'Aventure avec {n} points".replace("{n}", String.valueOf(parameters.score()));
    }

    @Override
    public Set<AutoChallengeTrigger> triggers() {
        return Set.of(AutoChallengeTrigger.STS_RUN_ENDED);
    }

    @Override
    public AutoChallengeProgress progress(StsBestScoreParameters parameters, UUID userId) {
        return AutoChallengeProgress.of(runRepository.findBestRetirementScore(userId), parameters.score());
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(StsBestScoreParameters parameters, AutoChallengeCandidateQuery query) {
        List<UUID> userIds = runRepository.findUsersWithRetirementScore(parameters.score(), query.challengeId(), query.afterUserId(), PageRequest.of(0, query.limit()));
        return new AutoChallengeCandidatePage(userIds, userIds.size() < query.limit() ? null : userIds.getLast());
    }
}
