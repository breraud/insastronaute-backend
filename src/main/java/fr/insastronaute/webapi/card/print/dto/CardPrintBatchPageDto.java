package fr.insastronaute.webapi.card.print.dto;

import java.util.List;

public record CardPrintBatchPageDto(
        String printPageId,
        Integer pageNumber,
        Integer sheetIndex,
        String side,
        List<CardPrintRunSlotDto> slots
) {
}
