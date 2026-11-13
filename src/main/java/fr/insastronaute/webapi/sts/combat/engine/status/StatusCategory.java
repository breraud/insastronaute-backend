package fr.insastronaute.webapi.sts.combat.engine.status;

/** How a status counts when an effect asks for "a buff" or "a debuff". */
public enum StatusCategory {

    BUFF,
    DEBUFF,

    /** Neither, so dispel, steal and transfer all leave it alone. */
    NEUTRAL,

    /**
     * A single status covering both directions: positive helps its holder, negative hurts them,
     * and zero means it has nothing left to do. Strength and Dexterity work this way, which is why
     * a card can apply "-2 Strength" as a debuff without a separate status for it.
     */
    SIGNED
}
