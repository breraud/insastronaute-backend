package fr.insastronaute.webapi.challenge.auto.types.stspvpwins;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidatePage;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameter;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameterType;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeProgress;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeType;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpProfileRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * "Win N duels". Friendly duels are opt-in because they cost nothing to arrange:
 * counting them by default would let two players farm the achievement.
 */
@Component
public final class StsPvpWinsChallengeType implements AutoChallengeType<StsPvpWinsParameters> {

    private final StsPvpProfileRepository profileRepository;

    public StsPvpWinsChallengeType(StsPvpProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Override
    public String key() {
        return "STS_PVP_WINS";
    }

    @Override
    public String publicName() {
        return "Gagner des duels";
    }

    @Override
    public String description() {
        return "Se déclenche quand le joueur a gagné un nombre donné de duels classés, amicaux inclus si l'option est cochée.";
    }

    @Override
    public Class<StsPvpWinsParameters> parametersType() {
        return StsPvpWinsParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of(
                AutoChallengeParameter.integer("count", "Nombre de victoires", 1, 100_000),
                new AutoChallengeParameter(
                        "includeFriendly", "Compter aussi les duels amicaux",
                        AutoChallengeParameterType.BOOLEAN, false, false, null, null, null
                )
        );
    }

    @Override
    public String phrase(StsPvpWinsParameters parameters) {
        String scope = parameters.includeFriendly() ? "duel(s)" : "duel(s) classé(s)";
        return "Gagne " + parameters.count() + " " + scope;
    }

    @Override
    public Set<AutoChallengeTrigger> triggers() {
        return Set.of(AutoChallengeTrigger.STS_PVP_BATTLE_SETTLED);
    }

    @Override
    public AutoChallengeProgress progress(StsPvpWinsParameters parameters, UUID userId) {
        long wins = profileRepository.findByUser_Id(userId)
                .map(profile -> (long) profile.getWinsRanked()
                        + (parameters.includeFriendly() ? profile.getWinsFriendly() : 0))
                .orElse(0L);
        return AutoChallengeProgress.of(wins, parameters.count());
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(StsPvpWinsParameters parameters, AutoChallengeCandidateQuery query) {
        List<UUID> userIds = profileRepository.findUsersWithPvpWins(
                parameters.count(), parameters.includeFriendly(), query.challengeId(), query.afterUserId(), PageRequest.of(0, query.limit())
        );
        return new AutoChallengeCandidatePage(userIds, userIds.size() < query.limit() ? null : userIds.getLast());
    }
}
