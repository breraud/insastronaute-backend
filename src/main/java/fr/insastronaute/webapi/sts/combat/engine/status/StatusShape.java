package fr.insastronaute.webapi.sts.combat.engine.status;

/**
 * Which of the two authored numbers a status actually carries.
 *
 * <p>An effect always writes both a value and a duration, but a status only reads what it is made
 * of: applying Poison reads the duration and ignores the value, applying Strength does the
 * opposite. Storing the unread number would show the player a figure the game never uses.
 */
public enum StatusShape {

    VALUE,
    DURATION,
    VALUE_AND_DURATION,

    /** Carries neither: the status decides its own strength and length. */
    FIXED
}
