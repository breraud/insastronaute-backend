package fr.insastronaute.webapi.challenge.auto.types.stspvpelo;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidatePage;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameter;
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
 * "Reach an elo of N". Elo can go down, so this unlocks the first time the
 * threshold is crossed and stays unlocked: an achievement records what a player
 * did, not what they still hold.
 */
@Component
public final class StsPvpEloChallengeType implements AutoChallengeType<StsPvpEloParameters> {

    private final StsPvpProfileRepository profileRepository;

    public StsPvpEloChallengeType(StsPvpProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Override
    public String key() {
        return "STS_PVP_ELO";
    }

    @Override
    public String publicName() {
        return "Atteindre un elo en duel";
    }

    @Override
    public String description() {
        return "Se déclenche la première fois que le joueur atteint cet elo. Le perdre ensuite ne retire pas le succès.";
    }

    @Override
    public Class<StsPvpEloParameters> parametersType() {
        return StsPvpEloParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of(AutoChallengeParameter.integer("elo", "Elo à atteindre", 1001, 100_000));
    }

    @Override
    public String phrase(StsPvpEloParameters parameters) {
        return "Atteins " + parameters.elo() + " d'elo en duel";
    }

    @Override
    public Set<AutoChallengeTrigger> triggers() {
        return Set.of(AutoChallengeTrigger.STS_PVP_BATTLE_SETTLED);
    }

    @Override
    public AutoChallengeProgress progress(StsPvpEloParameters parameters, UUID userId) {
        long elo = profileRepository.findByUser_Id(userId)
                .map(profile -> (long) profile.getElo())
                .orElse(0L);
        return AutoChallengeProgress.of(elo, parameters.elo());
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(StsPvpEloParameters parameters, AutoChallengeCandidateQuery query) {
        List<UUID> userIds = profileRepository.findUsersWithElo(
                parameters.elo(), query.challengeId(), query.afterUserId(), PageRequest.of(0, query.limit())
        );
        return new AutoChallengeCandidatePage(userIds, userIds.size() < query.limit() ? null : userIds.getLast());
    }
}
