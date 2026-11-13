package fr.insastronaute.webapi.user.dto;

public record AdminUserStatsDto(
        long submissionCount,
        long pendingSubmissionCount,
        long waitingConfirmationSubmissionCount,
        long validatedSubmissionCount,
        long rejectedSubmissionCount,
        long friendshipCount,
        long acceptedFriendshipCount,
        long pendingFriendshipCount,
        long virtualCardCount,
        long physicalCardCount,
        long physicalCardInstanceCount
) {
}
