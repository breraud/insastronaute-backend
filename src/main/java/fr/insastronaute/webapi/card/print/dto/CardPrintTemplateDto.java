package fr.insastronaute.webapi.card.print.dto;

import fr.insastronaute.webapi.card.print.domain.CardPrintFitMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CardPrintTemplateDto(
        UUID id,
        String name,
        String pdfUrl,
        Integer pageCount,
        Double pageWidthPt,
        Double pageHeightPt,
        Boolean enabled,
        CardPrintFitMode fitMode,
        Double bleedPt,
        List<CardPrintTemplateSlotDto> slots,
        CardPrintCodeSettingsDto code,
        List<CardPrintTemplateSlotDto> backSlots,
        CardPrintQrSettingsDto qr,
        Instant createdAt,
        Instant updatedAt
) {
}
