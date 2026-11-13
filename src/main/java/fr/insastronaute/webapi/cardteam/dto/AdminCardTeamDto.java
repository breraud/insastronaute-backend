package fr.insastronaute.webapi.cardteam.dto;

import fr.insastronaute.webapi.cardteam.domain.CardTeam;
import fr.insastronaute.webapi.cardteam.domain.CardTeamMember;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminCardTeamDto(
        UUID id,
        String name,
        int memberCount,
        int maxMembers,
        List<CardTeamUserDto> members,
        CardTeamUserDto creator,
        Instant createdAt
) {
    public static AdminCardTeamDto from(CardTeam team, List<CardTeamMember> members) {
        return new AdminCardTeamDto(
                team.getId(),
                team.getName(),
                members.size(),
                CardTeam.MAX_MEMBERS,
                members.stream().map(CardTeamMember::getUser).map(CardTeamUserDto::from).toList(),
                // Pas de garde sur le nul : la colonne est NOT NULL et sa cle
                // etrangere est en ON DELETE RESTRICT, donc une equipe persistee a
                // toujours un createur. La garde ne protegeait de rien et laissait
                // croire que le panel doit savoir afficher une equipe sans createur.
                CardTeamUserDto.from(team.getCreator()),
                team.getCreatedAt()
        );
    }
}
