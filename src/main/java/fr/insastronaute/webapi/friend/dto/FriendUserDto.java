package fr.insastronaute.webapi.friend.dto;

import fr.insastronaute.webapi.user.domain.AppUser;
import java.util.UUID;

public record FriendUserDto(
        UUID id,
        String displayName,
        String email,
        UUID departmentId
) {
    public static FriendUserDto from(AppUser user) {
        return new FriendUserDto(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getDepartment()
        );
    }
}
