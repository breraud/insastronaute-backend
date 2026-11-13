package fr.insastronaute.webapi.card.print.dto;

import java.util.UUID;

public record CardDatasetSheetCodeDto(
        UUID stripId,
        Integer position,
        String code
) {
}
