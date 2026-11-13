package fr.insastronaute.webapi.sts.combat.engine.status;

import java.util.Objects;

/**
 * What one status is, independently of any combatant holding it.
 *
 * @param id            the identifier the data and {@code StatusInstance} use
 * @param displayName   the authored name, kept so a client need not translate ids
 * @param category      whether it helps or hurts its holder
 * @param shape         which authored numbers it reads
 * @param framed        removable by cards and status effects, rather than only by expiry
 * @param goldFrame     resists removal even by effects that ignore resistance
 * @param inextendable  its duration cannot be increased
 * @param maxValue      how much of it one combatant can hold
 */
public record StatusDefinition(
        String id,
        String displayName,
        StatusCategory category,
        StatusShape shape,
        boolean framed,
        boolean goldFrame,
        boolean inextendable,
        int maxValue
) {

    public StatusDefinition {
        requireNonBlank(id, "id");
        requireNonBlank(displayName, "displayName");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(shape, "shape");
        if (maxValue < 0) {
            throw new IllegalArgumentException("maxValue must not be negative");
        }
    }

    public boolean isBuff(int value) {
        return category == StatusCategory.BUFF
                || (category == StatusCategory.SIGNED && value > 0);
    }

    public boolean isDebuff(int value) {
        return category == StatusCategory.DEBUFF
                || (category == StatusCategory.SIGNED && value < 0);
    }

    /** A signed status with nothing left on it is gone rather than kept at zero. */
    public boolean expiresAt(int value) {
        return category == StatusCategory.SIGNED && value == 0;
    }

    public boolean readsValue() {
        return shape == StatusShape.VALUE || shape == StatusShape.VALUE_AND_DURATION;
    }

    public boolean readsDuration() {
        return shape == StatusShape.DURATION || shape == StatusShape.VALUE_AND_DURATION;
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
