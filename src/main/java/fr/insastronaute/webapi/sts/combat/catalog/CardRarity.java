package fr.insastronaute.webapi.sts.combat.catalog;

import java.util.Optional;

/**
 * How rare a card is, mirroring Unity's {@code CardRarity}.
 *
 * <p>Long parsed and discarded, this became something the engine reads the day a random draw had
 * to know what it must never offer: {@link #SPECIAL} marks the cards that exist only because
 * something else creates them, and handing one out at random puts a card in a deck that nothing
 * was ever meant to put there.
 */
public enum CardRarity {

    COMMON("Common"),
    UNCOMMON("Uncommon"),
    RARE("Rare"),
    EPIC("Epic"),
    LEGENDARY("Legendary"),
    SPECIAL("Special");

    private final String jsonValue;

    CardRarity(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    public String jsonValue() {
        return jsonValue;
    }

    public static Optional<CardRarity> fromJson(String value) {
        for (CardRarity rarity : values()) {
            if (rarity.jsonValue.equals(value)) {
                return Optional.of(rarity);
            }
        }
        return Optional.empty();
    }
}
