package fr.insastronaute.webapi.friend.dto;

public record FriendSearchResultDto(
        FriendUserDto user,
        FriendRelationStatus relationStatus
) {
}
