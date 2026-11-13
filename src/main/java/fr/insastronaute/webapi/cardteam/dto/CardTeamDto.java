package fr.insastronaute.webapi.cardteam.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CardTeamDto(
        UUID id,
        String name,
        int memberCount,
        int maxMembers,
        List<CardTeamUserDto> members,
        Instant createdAt
) {
}
