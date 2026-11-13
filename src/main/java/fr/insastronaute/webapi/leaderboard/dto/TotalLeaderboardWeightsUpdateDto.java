package fr.insastronaute.webapi.leaderboard.dto;

import fr.insastronaute.webapi.leaderboard.domain.LeaderboardMetric;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

/**
 * @param factors ce par quoi les points de chaque metrique sont multiplies avant leur poids.
 *                Absent, les facteurs enregistres restent : un panneau qui ne les connait pas
 *                encore n'envoie que les poids.
 */
public record TotalLeaderboardWeightsUpdateDto(
        @NotEmpty Map<LeaderboardMetric, Integer> weights,
        Map<LeaderboardMetric, Integer> factors
) {
}
