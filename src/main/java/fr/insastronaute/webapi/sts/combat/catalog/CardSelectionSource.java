package fr.insastronaute.webapi.sts.combat.catalog;

import java.util.Optional;

/** Pile, or union of piles, a {@code CardSelection} draws its candidates from. */
public enum CardSelectionSource {

    HAND("Hand"),
    DRAW_PILE("DrawPile"),
    DISCARD_PILE("DiscardPile"),
    EXHAUST_PILE("ExhaustPile"),
    ALL("All"),
    ALL_EXCEPT_EXHAUST_PILE("AllExceptExhaustPile");

    private final String jsonValue;

    CardSelectionSource(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    public String jsonValue() {
        return jsonValue;
    }

    public static Optional<CardSelectionSource> fromJson(String value) {
        for (CardSelectionSource source : values()) {
            if (source.jsonValue.equals(value)) {
                return Optional.of(source);
            }
        }
        return Optional.empty();
    }
}
