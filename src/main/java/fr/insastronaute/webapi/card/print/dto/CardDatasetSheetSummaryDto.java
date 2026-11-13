package fr.insastronaute.webapi.card.print.dto;

import java.time.Instant;
import java.util.UUID;

public record CardDatasetSheetSummaryDto(
        UUID id,
        String sheetId,
        UUID batchId,
        Instant createdAt,
        long scanCount,
        boolean downloadable
) {
}
