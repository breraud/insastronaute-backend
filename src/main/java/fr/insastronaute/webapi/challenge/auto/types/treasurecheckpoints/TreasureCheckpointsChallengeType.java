package fr.insastronaute.webapi.challenge.auto.types.treasurecheckpoints;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidatePage;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameter;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeProgress;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeType;
import fr.insastronaute.webapi.treasurehunt.repository.PlayerCheckpointProgressRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public final class TreasureCheckpointsChallengeType implements AutoChallengeType<TreasureCheckpointsParameters> {

    private final PlayerCheckpointProgressRepository progressRepository;

    public TreasureCheckpointsChallengeType(PlayerCheckpointProgressRepository progressRepository) {
        this.progressRepository = progressRepository;
    }

    @Override
    public String key() {
        return "TREASURE_CHECKPOINTS";
    }

    @Override
    public String publicName() {
        return "Valider des checkpoints";
    }

    @Override
    public String description() {
        return "Se déclenche quand le joueur a validé un nombre donné de checkpoints de la chasse au trésor.";
    }

    @Override
    public Class<TreasureCheckpointsParameters> parametersType() {
        return TreasureCheckpointsParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of(AutoChallengeParameter.integer("count", "Nombre de checkpoints", 1, 1_000_000));
    }

    @Override
    public String phrase(TreasureCheckpointsParameters parameters) {
        return parameters.count() == 1
                ? "Valide un checkpoint de la chasse au trésor"
                : "Valide {n} checkpoints de la chasse au trésor".replace("{n}", String.valueOf(parameters.count()));
    }

    @Override
    public Set<AutoChallengeTrigger> triggers() {
        return Set.of(AutoChallengeTrigger.TREASURE_CHECKPOINT_VALIDATED);
    }

    @Override
    public AutoChallengeProgress progress(TreasureCheckpointsParameters parameters, UUID userId) {
        return AutoChallengeProgress.of(progressRepository.countValidatedCheckpoints(userId), parameters.count());
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(TreasureCheckpointsParameters parameters, AutoChallengeCandidateQuery query) {
        List<UUID> userIds = progressRepository.findUsersWithValidatedCheckpoints(parameters.count(), query.challengeId(), query.afterUserId(), PageRequest.of(0, query.limit()));
        return new AutoChallengeCandidatePage(userIds, userIds.size() < query.limit() ? null : userIds.getLast());
    }
}
