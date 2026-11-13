package fr.insastronaute.webapi.friend.dto;

import fr.insastronaute.webapi.friend.domain.Friendship;
import fr.insastronaute.webapi.friend.domain.FriendshipStatus;
import java.time.Instant;
import java.util.UUID;

public record FriendRequestDto(
        UUID id,
        FriendUserDto requester,
        FriendUserDto addressee,
        FriendshipStatus status,
        Instant createdAt,
        Instant respondedAt
) {
    public static FriendRequestDto from(Friendship friendship) {
        return new FriendRequestDto(
                friendship.getId(),
                FriendUserDto.from(friendship.getRequester()),
                FriendUserDto.from(friendship.getAddressee()),
                friendship.getStatus(),
                friendship.getCreatedAt(),
                friendship.getRespondedAt()
        );
    }
}
