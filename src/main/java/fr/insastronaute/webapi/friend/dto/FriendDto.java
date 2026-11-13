package fr.insastronaute.webapi.friend.dto;

import fr.insastronaute.webapi.friend.domain.Friendship;
import fr.insastronaute.webapi.user.domain.AppUser;
import java.time.Instant;
import java.util.UUID;

public record FriendDto(
        UUID friendshipId,
        FriendUserDto user,
        Instant friendsSince
) {
    public static FriendDto from(Friendship friendship, AppUser currentUser) {
        AppUser friend = friendship.getRequester().getId().equals(currentUser.getId())
                ? friendship.getAddressee()
                : friendship.getRequester();

        return new FriendDto(friendship.getId(), FriendUserDto.from(friend), friendship.getRespondedAt());
    }
}
