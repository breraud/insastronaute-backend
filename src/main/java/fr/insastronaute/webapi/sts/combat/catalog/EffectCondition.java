package fr.insastronaute.webapi.sts.combat.catalog;

import java.util.Objects;

/**
 * A test an effect must pass before it resolves.
 *
 * <p>{@code value} is what the test compares against and its meaning belongs to the type: a status
 * name for {@code TargetHasStatus}, a number for the threshold conditions, nothing at all for the
 * ones that only read what just happened.
 */
public record EffectCondition(ConditionType type, String value) {

    public EffectCondition {
        Objects.requireNonNull(type, "type");
        value = value == null ? "" : value;
    }

    /** @return the threshold this condition compares against, or empty when it is not a number */
    public java.util.OptionalInt threshold() {
        try {
            return java.util.OptionalInt.of(Integer.parseInt(value.trim()));
        } catch (NumberFormatException notANumber) {
            return java.util.OptionalInt.empty();
        }
    }
}
