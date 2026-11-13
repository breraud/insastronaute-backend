package fr.insastronaute.webapi.sts.combat.catalog;

import java.util.Optional;

/**
 * How a damaging effect looks when it lands, mirroring Unity's {@code AnimationType}.
 *
 * <p>Long read and discarded as decoration, this became load-bearing the day a relic had to name
 * a card after what it does: a blow's flavour -- fire, ice, a blade -- is the whole difference
 * between "Flamme" and "Lame", and nothing else in the effect carries it.
 */
public enum AnimationType {

    DEFAULT("Default"),
    SLASH("Slash"),
    MAGIC("Magic"),
    FIRE("Fire"),
    ICE("Ice"),
    THUNDER("Thunder"),
    LIGHT("Light"),
    EXPLOSION_SMALL("ExplosionSmall"),
    EXPLOSION_LARGE("ExplosionLarge");

    private final String jsonValue;

    AnimationType(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    public String jsonValue() {
        return jsonValue;
    }

    public static Optional<AnimationType> fromJson(String value) {
        for (AnimationType type : values()) {
            if (type.jsonValue.equals(value)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
