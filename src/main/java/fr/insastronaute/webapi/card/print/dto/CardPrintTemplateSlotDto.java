package fr.insastronaute.webapi.card.print.dto;

import java.util.UUID;

public record CardPrintTemplateSlotDto(
        UUID id,
        Integer slotIndex,
        Double xPt,
        Double yPt,
        Double widthPt,
        Double heightPt
) {
}
