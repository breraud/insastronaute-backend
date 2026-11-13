package fr.insastronaute.webapi.sts.combat.engine.status;

import fr.insastronaute.webapi.sts.combat.model.StatusInstance;

/**
 * What one status does to its holder at one moment of the turn.
 *
 * <p>Expressed as changes rather than as an action, so the turn machinery applies them all the
 * same way and a status is described by what it does rather than by how it does it.
 *
 * @param hpDelta         negative wounds its holder, positive heals it
 * @param appliedStatus   a status it puts on its holder, such as Strengthen turning into Strength
 * @param ticks           whether its own duration counts down at this moment
 * @param lethal          whether it takes its holder down outright
 * @param piercesArmor    whether its bite reaches HP without armour getting in the way
 */
public record StatusTurnEffect(
        int hpDelta,
        int armorDelta,
        int energyDelta,
        int cardsToDraw,
        StatusInstance appliedStatus,
        boolean ticks,
        boolean lethal,
        boolean piercesArmor,
        boolean clearsValue
) {

    /** The shape every effect but a piercing bite still uses: armour answers first. */
    public StatusTurnEffect(
            int hpDelta,
            int armorDelta,
            int energyDelta,
            int cardsToDraw,
            StatusInstance appliedStatus,
            boolean ticks,
            boolean lethal
    ) {
        this(hpDelta, armorDelta, energyDelta, cardsToDraw, appliedStatus, ticks, lethal, false,
                false);
    }

    /**
     * The shape a piercing bite uses, before accumulators had anything to empty.
     */
    public StatusTurnEffect(
            int hpDelta,
            int armorDelta,
            int energyDelta,
            int cardsToDraw,
            StatusInstance appliedStatus,
            boolean ticks,
            boolean lethal,
            boolean piercesArmor
    ) {
        this(hpDelta, armorDelta, energyDelta, cardsToDraw, appliedStatus, ticks, lethal,
                piercesArmor, false);
    }

    /**
     * The same effect, emptying the accumulator its status keeps on its own value.
     *
     * <p>Time Stop is spent by expiring, so it never had to be emptied. A status that hands back
     * what it swallowed <em>every</em> turn and stays does: without this it would pay out the
     * same debt again on the next turn, and the one after that.
     */
    public StatusTurnEffect clearingValue() {
        return new StatusTurnEffect(hpDelta, armorDelta, energyDelta, cardsToDraw, appliedStatus,
                ticks, lethal, piercesArmor, true);
    }

    private static final StatusTurnEffect NOTHING =
            new StatusTurnEffect(0, 0, 0, 0, null, false, false);
    private static final StatusTurnEffect TICK =
            new StatusTurnEffect(0, 0, 0, 0, null, true, false);

    /** The status does nothing at this moment, not even count down. */
    public static StatusTurnEffect nothing() {
        return NOTHING;
    }

    /** The status only counts down. */
    public static StatusTurnEffect tick() {
        return TICK;
    }

    /**
     * A bite armour can soak, which is what every damaging status but one is.
     *
     * <p>Unity puts these through {@code Character.TakeDamage}, where armour absorbs first; the
     * engine used to subtract them straight off HP, making Burn and Poison quietly ignore a
     * defence the player had paid for.
     */
    public static StatusTurnEffect damage(int amount) {
        return new StatusTurnEffect(-amount, 0, 0, 0, null, true, false);
    }

    /** A bite that goes straight to HP however much armour its holder is standing behind. */
    public static StatusTurnEffect piercingDamage(int amount) {
        return new StatusTurnEffect(-amount, 0, 0, 0, null, true, false, true);
    }

    public static StatusTurnEffect heal(int amount) {
        return new StatusTurnEffect(amount, 0, 0, 0, null, true, false);
    }

    public static StatusTurnEffect armor(int amount) {
        return new StatusTurnEffect(0, amount, 0, 0, null, true, false);
    }

    public static StatusTurnEffect energy(int amount) {
        return new StatusTurnEffect(0, 0, amount, 0, null, false, false);
    }

    public static StatusTurnEffect draw(int cards) {
        return new StatusTurnEffect(0, 0, 0, cards, null, true, false);
    }

    public static StatusTurnEffect applying(StatusInstance status) {
        return new StatusTurnEffect(0, 0, 0, 0, status, false, false);
    }

    /**
     * The same effect, counting its own duration down.
     *
     * <p>{@link #applying} does not tick: the statuses that hand out another one -- Accelerate,
     * Strengthen -- are spent by expiring rather than by counting. "Statuts" is the exception. It
     * inflicts one of three afflictions every turn <em>and</em> runs out, and built from
     * {@code applying} alone it inflicted them forever.
     */
    public StatusTurnEffect withTick() {
        return new StatusTurnEffect(
                hpDelta, armorDelta, energyDelta, cardsToDraw, appliedStatus, true, lethal,
                piercesArmor, clearsValue);
    }

    public StatusTurnEffect withoutTick() {
        return new StatusTurnEffect(
                hpDelta, armorDelta, energyDelta, cardsToDraw, appliedStatus, false, lethal,
                piercesArmor, clearsValue);
    }

    public StatusTurnEffect withHpDelta(int hpDelta) {
        return new StatusTurnEffect(
                hpDelta, armorDelta, energyDelta, cardsToDraw, appliedStatus, ticks, lethal,
                piercesArmor, clearsValue);
    }

    public boolean isNothing() {
        return equals(NOTHING);
    }
}
