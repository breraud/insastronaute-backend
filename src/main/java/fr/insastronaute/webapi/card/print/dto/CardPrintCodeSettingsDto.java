package fr.insastronaute.webapi.card.print.dto;

import fr.insastronaute.webapi.card.print.domain.CardPrintFontFamily;
import fr.insastronaute.webapi.card.print.domain.CardPrintFontWeight;
import fr.insastronaute.webapi.card.print.domain.CardPrintTextAlign;

public record CardPrintCodeSettingsDto(
        Double xPt,
        Double yPt,
        Double widthPt,
        Double heightPt,
        CardPrintFontFamily fontFamily,
        CardPrintFontWeight fontWeight,
        Double fontSizePt,
        String color,
        String backgroundColor,
        Double backgroundOpacity,
        CardPrintTextAlign align
) {
}
