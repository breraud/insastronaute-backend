package fr.insastronaute.webapi.card.print.dto;

import fr.insastronaute.webapi.card.print.domain.CardPrintRunStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CardPrintBatchDto(
        UUID id,
        UUID templateId,
        String templateName,
        Integer sheetCount,
        Integer pageCount,
        Integer instanceCount,
        CardPrintRunStatus status,
        String pdfUrl,
        List<CardPrintBatchPageDto> pages,
        List<CardPrintRunSlotDto> slots,
        Instant createdAt,
        Instant completedAt
) {
}
