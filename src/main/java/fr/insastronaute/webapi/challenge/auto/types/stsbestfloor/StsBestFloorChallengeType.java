package fr.insastronaute.webapi.challenge.auto.types.stsbestfloor;

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

/**
 * "Reach floor N in Aventure". Counts the deepest floor over every run, finished
 * or not: dying on floor 30 is still reaching floor 30.
 */
@Component
public final class StsBestFloorChallengeType implements AutoChallengeType<StsBestFloorParameters> {

    private final StsRunRepository runRepository;

    public StsBestFloorChallengeType(StsRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @Override
    public String key() {
        return "STS_BEST_FLOOR";
    }

    @Override
    public String publicName() {
        return "Atteindre un étage en Aventure";
    }

    @Override
    public String description() {
        return "Se déclenche quand le joueur a atteint un étage donné, que la run se soit bien terminée ou non.";
    }

    @Override
    public Class<StsBestFloorParameters> parametersType() {
        return StsBestFloorParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of(AutoChallengeParameter.integer("floor", "Étage à atteindre", 1, 10_000));
    }

    @Override
    public String phrase(StsBestFloorParameters parameters) {
        return "Atteins l'étage " + parameters.floor() + " en Aventure";
    }

    @Override
    public Set<AutoChallengeTrigger> triggers() {
        return Set.of(AutoChallengeTrigger.STS_RUN_ENDED);
    }

    @Override
    public AutoChallengeProgress progress(StsBestFloorParameters parameters, UUID userId) {
        return AutoChallengeProgress.of(runRepository.findDeepestFloor(userId), parameters.floor());
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(StsBestFloorParameters parameters, AutoChallengeCandidateQuery query) {
        List<UUID> userIds = runRepository.findUsersReachingFloor(
                parameters.floor(), query.challengeId(), query.afterUserId(), PageRequest.of(0, query.limit())
        );
        return new AutoChallengeCandidatePage(userIds, userIds.size() < query.limit() ? null : userIds.getLast());
    }
}
