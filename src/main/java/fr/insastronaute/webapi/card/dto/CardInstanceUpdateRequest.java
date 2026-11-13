package fr.insastronaute.webapi.card.dto;

import java.util.UUID;

public record CardInstanceUpdateRequest(
        UUID cardId,
        String publicId,
        UUID ownerUserId,
        Boolean clearOwner
) {
}
