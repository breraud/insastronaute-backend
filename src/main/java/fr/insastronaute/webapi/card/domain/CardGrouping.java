package fr.insastronaute.webapi.card.domain;

import fr.insastronaute.webapi.common.web.BadRequestException;

public enum CardGrouping {
    RANK, TYPE;

    public static CardGrouping from(String value) {
        if (value == null || value.isBlank() || "rank".equalsIgnoreCase(value)) return RANK;
        if ("type".equalsIgnoreCase(value)) return TYPE;
        throw new BadRequestException("Le regroupement doit être 'rank' ou 'type'.");
    }
}
