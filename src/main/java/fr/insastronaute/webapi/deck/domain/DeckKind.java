package fr.insastronaute.webapi.deck.domain;

/**
 * What the shop is selling. A single card is a deck whose only tier drops it at 100%,
 * so both share the purchase pipeline; this only tells the shop how to present it.
 */
public enum DeckKind {
    PACK,
    CARD
}
