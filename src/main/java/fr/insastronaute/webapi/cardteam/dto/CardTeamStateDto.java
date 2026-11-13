package fr.insastronaute.webapi.cardteam.dto;

import java.util.List;

public record CardTeamStateDto(
        CardTeamDto team,
        List<CardTeamSummaryDto> discoverableTeams,
        List<CardTeamInvitationDto> invitations,
        List<CardTeamJoinRequestDto> outgoingRequests,
        List<CardTeamJoinRequestDto> incomingRequests,
        List<CardTeamOutgoingInvitationDto> outgoingInvitations
) {
}
