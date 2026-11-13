package fr.insastronaute.webapi.sts.combat.catalog;

import java.util.Optional;

/**
 * Who a card is played at.
 *
 * <p>{@link #NONE} means the client shows no target picker, so the command carries no target and
 * every effect resolves on the caster. It is kept apart from {@link #SELF} because the two differ
 * for the client even though they resolve the same way.
 *
 * <p>{@link #ANY_PLAYER} lets the client pick any living combatant on the caster's team, whereas
 * {@link #SELF} always means the acting combatant alone.
 */
public enum TargetingMode {

    SELF("Player"),
    ANY_PLAYER("AnyPlayer"),
    SINGLE_ENEMY("Enemy"),
    ALL_ENEMIES("AllEnemies"),
    ALL_CHARACTERS("AllCharacters"),
    RANDOM_ENEMY("RandomEnemy"),
    NONE("None");

    private final String jsonValue;

    TargetingMode(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    /**
     * The spelling this aim is written as, everywhere outside this class.
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
    public static TargetingMode fromWire(String value) {
        String wanted = value == null ? "" : value.trim();
        for (TargetingMode candidate : values()) {
            if (candidate.jsonValue.equals(wanted) || candidate.name().equalsIgnoreCase(wanted)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown TargetingMode: " + value);
    }

    public static Optional<TargetingMode> fromJson(String value) {
        for (TargetingMode mode : values()) {
            if (mode.jsonValue.equals(value)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }
}
