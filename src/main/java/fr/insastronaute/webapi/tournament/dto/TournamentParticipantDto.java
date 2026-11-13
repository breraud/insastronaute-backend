package fr.insastronaute.webapi.tournament.dto;

import fr.insastronaute.webapi.event.domain.EventRegistrationStatus;

import java.time.Instant;
import java.util.UUID;

public record TournamentParticipantDto(
        UUID registrationId,
        UUID userId,
        String displayName,
        String email,
        UUID departmentId,
        String departmentShortName,
        EventRegistrationStatus status,
        Boolean attended,
        Instant registeredAt,
        int awardedParticipationPoints,
        int awardedVictoryPoints,
        int awardedTotalPoints,
        String awardedTierLabel,
        String awardedTeamName
) {
}
