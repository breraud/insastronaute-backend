package fr.insastronaute.webapi.card.print.dto;

public record CardPrintPdfRegenerationDto(
        int scannedCount,
        int skippedCount,
        int regeneratedCount,
        int failedCount
) {
}
