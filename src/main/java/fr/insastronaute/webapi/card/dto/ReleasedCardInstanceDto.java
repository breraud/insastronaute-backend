package fr.insastronaute.webapi.card.dto;

import java.util.UUID;

public record ReleasedCardInstanceDto(UUID cardId, String cardName, String publicId, int remainingQuantity) {
}
