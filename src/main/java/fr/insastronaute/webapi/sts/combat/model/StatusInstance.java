package fr.insastronaute.webapi.sts.combat.model;

/**
 * One status a combatant is carrying.
 *
 * <p>{@code removable} and {@code resistsEverything} are an exception to the catalog rather than a
 * copy of it: left null, the status behaves like every other of its kind. Unity carries those two
 * flags on the instance, and two boss relics rely on it — Titan Heart's Fragile and Cathedral
 * Knell's Weakness are the price those relics charge, and they are meant to stick where an
 * ordinary Fragile or Weakness can be dispelled away.
 *
 * @param removable          false to keep any card from stripping this instance, null to let the
 *                           catalog answer for its kind
 * @param resistsEverything  true to survive even an effect that ignores resistance, null for the
 *                           catalog's answer
 * @param progress           qualifying cards seen since a follow-up status last fired, and what a
 *                           Regulator has let through this turn; unread by every other status
 */
public record StatusInstance(
        String statusType,
        int value,
        int duration,
        String cardId,
        int index,
        Boolean removable,
        Boolean resistsEverything,
        int progress
) {

    public StatusInstance {
        requireNonBlank(statusType, "statusType");
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative");
        }
        cardId = cardId == null ? "" : cardId;
    }

    /** A status that behaves like every other of its kind. */
    public StatusInstance(String statusType, int value, int duration, String cardId, int index) {
        this(statusType, value, duration, cardId, index, null, null, 0);
    }

    /** A status that, this once, nothing can take off, whatever its kind normally allows. */
    public static StatusInstance unremovable(String statusType, int value, int duration) {
        return new StatusInstance(statusType, value, duration, "", 0, false, true, 0);
    }

    /** @return the same status, carrying the removability of {@code other} */
    public StatusInstance withResistanceOf(StatusInstance other) {
        return new StatusInstance(statusType, value, duration, cardId, index,
                other.removable(), other.resistsEverything(), progress);
    }

    /** @return the same status, having just counted one more qualifying card played */
    public StatusInstance withProgress(int progress) {
        return new StatusInstance(statusType, value, duration, cardId, index,
                removable, resistsEverything, progress);
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}

