package fr.insastronaute.webapi.sts.combat.catalog;

import java.util.Optional;

/**
 * Card family as authored in Unity.
 *
 * <p>{@link #NONE} carries Unity's {@code "Rien"} and also the absence of the field: both mean
 * the card claims no family, which matters because the family drives the Attack/Skill/Power
 * card selection filters.
 */
public enum CardType {

    ATTACK("Attaque"),
    SKILL("Compétence"),
    POWER("Pouvoir"),
    NONE("Rien");

    private final String jsonValue;

    CardType(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    /**
     * The spelling this card family is written as, everywhere outside this class.
     *
     * <p>Annotated so a saved combat and a snapshot both carry Unity's vocabulary rather than the
     * Java constant name. The client parses what it is sent against its own enum, and
     * {@code ATTACK} is not {@code Attaque} and {@code SINGLE_ENEMY} is not {@code Enemy}: with
     * the constant names on the wire, every per-instance override the server sent was quietly
     * unreadable at the other end.
     */
    @com.fasterxml.jackson.annotation.JsonValue
    public String jsonValue() {
        return jsonValue;
    }

    /**
     * Reads either vocabulary.
     *
     * <p>Lenient on purpose: combats saved before {@link #jsonValue()} became the written form
     * hold the Java constant name, and they have to keep loading. New writes are always the
     * authored spelling.
     */
    @com.fasterxml.jackson.annotation.JsonCreator
    public static CardType fromWire(String value) {
        String wanted = value == null ? "" : value.trim();
        for (CardType candidate : values()) {
            if (candidate.jsonValue.equals(wanted) || candidate.name().equalsIgnoreCase(wanted)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown CardType: " + value);
    }

    public static Optional<CardType> fromJson(String value) {
        for (CardType type : values()) {
            if (type.jsonValue.equals(value)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
