package fr.insastronaute.webapi.card.print.dto;

import fr.insastronaute.webapi.card.print.domain.CardPrintQrCorner;

public record CardPrintQrSettingsDto(
        Boolean enabled,
        CardPrintQrCorner corner,
        Double sizePt,
        Integer marginModules,
        Double insetPt
) {
}
