package fr.insastronaute.webapi.challenge.auto.api;

public enum AutoChallengeParameterType {
    INTEGER,
    BOOLEAN,
    TEXT,
    DATE,
    SELECT,
    CARD_CATEGORY,
    /** Le type de la carte : ce qu'elle represente, quand la categorie porte sa rarete. */
    CARD_TYPE,
    CARD
}
