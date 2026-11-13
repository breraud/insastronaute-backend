package fr.insastronaute.webapi.challenge.auto.types.stsrunsfinished;

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
public final class StsRunsFinishedChallengeType implements AutoChallengeType<StsRunsFinishedParameters> {

    private final StsRunRepository runRepository;

    public StsRunsFinishedChallengeType(StsRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @Override
    public String key() {
        return "STS_RUNS_FINISHED";
    }

    @Override
    public String publicName() {
        return "Terminer des runs d'Aventure";
    }

    @Override
    public String description() {
        return "Se déclenche quand le joueur a mené un nombre donné de runs jusqu'à la retraite, l'issue qui banque le score.";
    }

    @Override
    public Class<StsRunsFinishedParameters> parametersType() {
        return StsRunsFinishedParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of(AutoChallengeParameter.integer("count", "Nombre de runs terminées", 1, 10_000_000));
    }

    @Override
    public String phrase(StsRunsFinishedParameters parameters) {
        return "Termine {n} run(s) d'Aventure".replace("{n}", String.valueOf(parameters.count()));
    }

    @Override
    public Set<AutoChallengeTrigger> triggers() {
        return Set.of(AutoChallengeTrigger.STS_RUN_ENDED);
    }

    @Override
    public AutoChallengeProgress progress(StsRunsFinishedParameters parameters, UUID userId) {
        return AutoChallengeProgress.of(runRepository.countFinishedRuns(userId), parameters.count());
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(StsRunsFinishedParameters parameters, AutoChallengeCandidateQuery query) {
        List<UUID> userIds = runRepository.findUsersWithFinishedRuns(parameters.count(), query.challengeId(), query.afterUserId(), PageRequest.of(0, query.limit()));
        return new AutoChallengeCandidatePage(userIds, userIds.size() < query.limit() ? null : userIds.getLast());
    }
}
