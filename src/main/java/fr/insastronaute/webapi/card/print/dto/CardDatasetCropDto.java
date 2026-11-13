package fr.insastronaute.webapi.card.print.dto;

import fr.insastronaute.webapi.card.print.domain.CardDatasetCropFraming;
import fr.insastronaute.webapi.card.print.domain.CardDatasetCropStatus;

import java.time.Instant;
import java.util.UUID;

public record CardDatasetCropDto(
        UUID id,
        String sheetId,
        UUID stripId,
        Integer position,
        String expectedCode,
        String label,
        CardDatasetCropStatus status,
        CardDatasetCropFraming framing,
        String imageContentType,
        Long imageByteSize,
        Integer imageWidthPx,
        Integer imageHeightPx,
        String captureDevice,
        Integer captureWidthPx,
        Integer captureHeightPx,
        Instant createdAt
) {
}
