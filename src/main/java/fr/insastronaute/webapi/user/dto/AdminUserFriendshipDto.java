package fr.insastronaute.webapi.user.dto;

import fr.insastronaute.webapi.friend.domain.FriendshipStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminUserFriendshipDto(
        UUID friendshipId,
        FriendshipStatus status,
        String direction,
        UUID userId,
        String displayName,
        String email,
        Instant createdAt,
        Instant respondedAt
) {
}
