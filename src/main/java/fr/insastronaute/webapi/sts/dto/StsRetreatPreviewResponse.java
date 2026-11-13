package fr.insastronaute.webapi.sts.dto;

import fr.insastronaute.webapi.sts.domain.StsRunStatus;

import java.util.UUID;

public record StsRetreatPreviewResponse(
        boolean accepted,
        UUID runId,
        StsRunStatus status,
        long score,
        long tokensPreview,
        long projectedTokenBalance,
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
        long minimumReward
) {
}
