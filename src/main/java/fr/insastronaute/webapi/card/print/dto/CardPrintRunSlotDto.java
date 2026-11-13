package fr.insastronaute.webapi.card.print.dto;

import java.util.UUID;

public record CardPrintRunSlotDto(
        Integer pageIndex,
        Integer slotIndex,
        UUID cardId,
        String cardName,
        UUID cardInstanceId,
        String publicId
) {
}
