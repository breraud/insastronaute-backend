package fr.insastronaute.webapi.sts.dto;

import fr.insastronaute.webapi.sts.domain.StsRunStatus;

import java.util.List;
import java.util.UUID;

public record StsRetireRunResponse(
        boolean accepted,
        UUID runId,
        StsRunStatus status,
        long score,
        long tokensGranted,
        long tokenBalance,
        long visitedNodeScore,
        long combatVictoryScore,
        long eliteVictoryScore,
        long eventVisitedScore,
        long actReachedScore,
        long relicOwnedScore,
        long deckCardScore,
        long goldOwnedScore,
        long remainingHpPercentScore,
        long scorePerToken,
        String rounding,
        long minimumReward,
        // The full deck-derived PvP unlocks a retreat grants, empty when this run had already
        // claimed them or nothing was eligible.
        List<String> pvpUnlockedCardNames
) {
}
