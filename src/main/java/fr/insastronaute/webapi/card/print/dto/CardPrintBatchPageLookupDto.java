package fr.insastronaute.webapi.card.print.dto;

import java.util.UUID;

public record CardPrintBatchPageLookupDto(
        UUID batchId,
        String printPageId,
        Integer pageNumber,
        Integer sheetIndex,
        String side,
        String pdfUrl,
        CardPrintBatchPageDto page
) {
}
