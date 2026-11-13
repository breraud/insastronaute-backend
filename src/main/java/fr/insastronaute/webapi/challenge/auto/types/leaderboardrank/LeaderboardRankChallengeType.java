package fr.insastronaute.webapi.challenge.auto.types.leaderboardrank;

import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidatePage;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeCandidateQuery;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameter;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeParameterType;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeProgress;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeTrigger;
import fr.insastronaute.webapi.challenge.auto.api.AutoChallengeType;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardMetric;
import fr.insastronaute.webapi.leaderboard.repository.LeaderboardEntryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * "Reach the top N of a leaderboard". Reads released snapshots, so a podium held
 * last week still counts: an achievement records what you did, not where you
 * happen to stand today.
 */
@Component
public final class LeaderboardRankChallengeType implements AutoChallengeType<LeaderboardRankParameters> {

    private final LeaderboardEntryRepository entryRepository;

    public LeaderboardRankChallengeType(LeaderboardEntryRepository entryRepository) {
        this.entryRepository = entryRepository;
    }

    @Override
    public String key() {
        return "LEADERBOARD_RANK";
    }

    @Override
    public String publicName() {
        return "Atteindre un rang au classement";
    }

    @Override
    public String description() {
        return "Se déclenche quand le joueur a été classé au moins une fois dans les N premiers d'un classement publié.";
    }

    @Override
    public Class<LeaderboardRankParameters> parametersType() {
        return LeaderboardRankParameters.class;
    }

    @Override
    public List<AutoChallengeParameter> parameters() {
        return List.of(
                new AutoChallengeParameter(
                        "metric", "Classement",
                        AutoChallengeParameterType.SELECT, true, null, null, null,
                        Arrays.stream(LeaderboardMetric.values()).map(Enum::name).toList()
                ),
                AutoChallengeParameter.integer("maxRank", "Rang à atteindre (ou mieux)", 1, 10_000)
        );
    }

    @Override
    public String phrase(LeaderboardRankParameters parameters) {
        String metric = switch (parameters.metric()) {
            case TOURNAMENT_POINTS -> "des tournois";
            case CHALLENGE_POINTS -> "des défis";
            case PHYSICAL_COLLECTION_POINTS -> "des cartes physiques";
            case VIRTUAL_COLLECTION_POINTS -> "des cartes virtuelles";
            case GAME_POINTS -> "du jeu";
            case PVP_ELO -> "Elo du multijoueur";
        };
        return parameters.maxRank() == 1
                ? "Prends la première place du classement " + metric
                : "Entre dans le top " + parameters.maxRank() + " du classement " + metric;
    }

    @Override
    public Set<AutoChallengeTrigger> triggers() {
        // Ranks only change when a snapshot is released, which is itself
        // scheduled: the hourly sweep is the natural home for this one.
        return Set.of();
    }

    @Override
    public AutoChallengeProgress progress(LeaderboardRankParameters parameters, UUID userId) {
        int bestRank = entryRepository.findBestRankEver(userId, parameters.metric());
        // 0 means "never ranked". Progress is binary here: a rank is not a
        // quantity a player accumulates toward.
        boolean reached = bestRank > 0 && bestRank <= parameters.maxRank();
        return AutoChallengeProgress.of(reached ? 1 : 0, 1);
    }

    @Override
    public AutoChallengeCandidatePage findCandidates(LeaderboardRankParameters parameters, AutoChallengeCandidateQuery query) {
        List<UUID> userIds = entryRepository.findUsersEverRankedWithin(
                parameters.metric(), parameters.maxRank(), query.challengeId(), query.afterUserId(), PageRequest.of(0, query.limit())
        );
        return new AutoChallengeCandidatePage(userIds, userIds.size() < query.limit() ? null : userIds.getLast());
    }
}
