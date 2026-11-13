package fr.insastronaute.webapi.challenge.dto;

public enum ModerationHistoryType {
    VALIDATED,
    REJECTED,
    MANUAL_GRANT,
    /** Les points bonus d'un defi deja valide, revus apres coup. */
    BONUS_ADJUSTED
}
