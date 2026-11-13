package fr.insastronaute.webapi.medialibrary.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Une personne designee. {@code stillStaff} est faux quand la ligne survit a la perte
 * du role de staff : elle reste affichee, mais n'ouvre plus rien.
 */
public record MediaLibraryGrantDto(
        UUID userId,
        String displayName,
        String email,
        boolean stillStaff,
        String grantedByName,
        Instant grantedAt
) {
}
