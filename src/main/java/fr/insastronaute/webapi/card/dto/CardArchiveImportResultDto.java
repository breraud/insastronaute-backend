package fr.insastronaute.webapi.card.dto;

public record CardArchiveImportResultDto(
        int typeCount,
        int categoryCount,
        int cardCount,
        int instanceCount,
        int templateCount,
        int printRunCount,
        int objectCount
) {
}
