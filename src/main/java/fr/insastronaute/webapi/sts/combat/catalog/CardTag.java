package fr.insastronaute.webapi.sts.combat.catalog;

import java.util.Optional;

/**
 * Authored marker carried by a card, mirroring Unity's {@code CardTag}.
 *
 * <p>Tags are data the card selection filters read, so an unknown tag must fail loudly rather
 * than be dropped: a silently ignored {@code Atom} turns "exhaust every Atom" into "exhaust
 * nothing".
 */
public enum CardTag {

    NONE("None"),
    ATTACK("Attack"),
    SKILL("Skill"),
    POWER("Power"),
    STATUS("Status"),
    CURSE("Curse"),
    CREATED("Created"),
    UNOBTAINABLE("Unobtainable"),
    FOLLOW_UP("FollowUp"),
    EXHAUST("Exhaust"),
    RETAIN("Retain"),
    ETHEREAL("Ethereal"),
    INNATE("Innate"),
    INFINITE("Infinite"),
    ATOM("Atom"),
    MOLECULE("Molecule"),
    NORM("Norm"),
    DEVELOPER("Developer"),
    AUTOMATIC("Automatic"),
    UNPLAYABLE("Unplayable"),
    /** Jouer une carte qui le porte ouvre le pool secret pour tout le compte, définitivement. */
    SECRET_UNLOCK("SecretUnlock"),
    /** Ne peut tomber en récompense qu'une fois le pool secret ouvert. */
    SECRET("Secret");

    private final String jsonValue;

    CardTag(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    /**
     * The spelling this card marker is written as, everywhere outside this class.
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
    public static CardTag fromWire(String value) {
        String wanted = value == null ? "" : value.trim();
        for (CardTag candidate : values()) {
            if (candidate.jsonValue.equals(wanted) || candidate.name().equalsIgnoreCase(wanted)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown CardTag: " + value);
    }

    public static Optional<CardTag> fromJson(String value) {
        for (CardTag tag : values()) {
            if (tag.jsonValue.equals(value)) {
                return Optional.of(tag);
            }
        }
        return Optional.empty();
    }
}
