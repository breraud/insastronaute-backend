package fr.insastronaute.webapi.cardteam.dto;

import java.time.Instant;
import java.util.UUID;

public record CardTeamJoinRequestDto(UUID id, CardTeamSummaryDto team, CardTeamUserDto requester, Instant createdAt) {
}
