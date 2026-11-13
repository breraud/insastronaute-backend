package fr.insastronaute.webapi.card.print.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CardDatasetSheetDto(
        UUID id,
        String sheetId,
        UUID batchId,
        Instant createdAt,
        List<CardDatasetSheetCodeDto> codes
) {
}
