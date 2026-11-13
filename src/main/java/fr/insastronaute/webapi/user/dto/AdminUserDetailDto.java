package fr.insastronaute.webapi.user.dto;

import fr.insastronaute.webapi.card.dto.AdminCardInstanceDto;
import fr.insastronaute.webapi.cardteam.dto.AdminCardTeamDto;

import java.util.List;

public record AdminUserDetailDto(
        AdminUserDto user,
        AdminUserStatsDto stats,
        List<AdminUserSubmissionDto> submissions,
        List<AdminUserFriendshipDto> friendships,
        List<AdminUserCardCollectionDto> cardCollections,
        List<AdminCardInstanceDto> physicalCardInstances,
        AdminCardTeamDto cardTeam
) {
}
