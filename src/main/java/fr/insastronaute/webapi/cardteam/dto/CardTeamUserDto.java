package fr.insastronaute.webapi.cardteam.dto;

import fr.insastronaute.webapi.user.domain.AppUser;
import org.springframework.util.StringUtils;

import java.util.UUID;

public record CardTeamUserDto(UUID id, String displayName) {
    public static CardTeamUserDto from(AppUser user) {
        String name = StringUtils.hasText(user.getDisplayName()) ? user.getDisplayName() : user.getEmail();
        return new CardTeamUserDto(user.getId(), name);
    }
}
