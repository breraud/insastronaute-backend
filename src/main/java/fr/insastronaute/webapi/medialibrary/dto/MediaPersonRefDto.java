package fr.insastronaute.webapi.medialibrary.dto;

import fr.insastronaute.webapi.user.domain.AppUser;

import java.util.UUID;

public record MediaPersonRefDto(UUID userId, String displayName) {

    public static MediaPersonRefDto of(AppUser user) {
        return new MediaPersonRefDto(user.getId(), nameOf(user));
    }

    public static String nameOf(AppUser user) {
        if (user == null) {
            return "Joueur inconnu";
        }
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        return "Joueur inconnu";
    }
}
