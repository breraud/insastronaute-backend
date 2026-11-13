package fr.insastronaute.webapi.leaderboard.dto;

import fr.insastronaute.webapi.leaderboard.domain.LeaderboardMetric;

import java.time.Instant;
import java.util.List;

public record DepartmentLeaderboardDto(
        LeaderboardMetric metric,
        Instant calculatedAt,
        /** La formule appliquee a ce classement. Nulle sur un classement publie avant. */
        String formula,
        /** L'effectif du plus grand departement : l'echelle a laquelle les autres sont ramenes. */
        Integer referenceHeadcount,
        /**
         * Le poids de cette metrique dans le classement total, en pourcentage : le meme que
         * celui du total des joueurs. Nul quand la metrique n'a pas de configuration joueur.
         */
        Integer totalWeightPercent,
        /**
         * Ce par quoi ses points sont multiplies avant le poids, dans le total seulement : le
         * meme que celui du total des joueurs. Nul quand la metrique n'a pas de configuration joueur.
         */
        Integer totalScaleFactor,
        List<DepartmentLeaderboardEntryDto> entries
) {
    public DepartmentLeaderboardDto(LeaderboardMetric metric, Instant calculatedAt, List<DepartmentLeaderboardEntryDto> entries) {
        this(metric, calculatedAt, null, null, null, null, entries);
    }

    public DepartmentLeaderboardDto(LeaderboardMetric metric, Instant calculatedAt, String formula,
                                    Integer referenceHeadcount, List<DepartmentLeaderboardEntryDto> entries) {
        this(metric, calculatedAt, formula, referenceHeadcount, null, null, entries);
    }
}
