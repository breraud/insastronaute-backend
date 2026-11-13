package fr.insastronaute.webapi.treasurehunt.dto;

import java.util.List;
import java.util.UUID;

/** Une voie vue par l'administration : qui l'a commencee, qui l'a terminee, et ce qui s'est passe entre. */
public record TreasureHuntPathStatsResponse(
        UUID pathId,
        int position,
        String name,
        String departmentShortName,
        long startedCount,
        long completedCount,
        List<TreasureHuntStepStatsResponse> steps
) { }
