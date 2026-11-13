package fr.insastronaute.webapi.challenge.auto.types.leaderboardrank;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.insastronaute.webapi.leaderboard.domain.LeaderboardMetric;

public record LeaderboardRankParameters(LeaderboardMetric metric, int maxRank) {

    @JsonCreator
    public LeaderboardRankParameters(
            @JsonProperty("metric") String metric,
            @JsonProperty("maxRank") Integer maxRank
    ) {
        this(parseMetric(metric), normalize(maxRank));
    }

    private static LeaderboardMetric parseMetric(String metric) {
        if (metric == null || metric.isBlank()) {
            throw new IllegalArgumentException("La métrique de classement est requise.");
        }
        try {
            return LeaderboardMetric.valueOf(metric.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Métrique de classement inconnue : " + metric);
        }
    }

    private static int normalize(Integer maxRank) {
        int value = maxRank == null ? 3 : maxRank;
        if (value < 1) {
            throw new IllegalArgumentException("Le rang visé doit être au moins 1.");
        }
        return value;
    }
}
