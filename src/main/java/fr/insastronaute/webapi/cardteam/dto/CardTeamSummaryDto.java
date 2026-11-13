package fr.insastronaute.webapi.cardteam.dto;

import java.util.List;
import java.util.UUID;

public record CardTeamSummaryDto(
        UUID id,
        String name,
        int memberCount,
        int maxMembers,
        List<CardTeamUserDto> friends
) {
}
