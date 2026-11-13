package fr.insastronaute.webapi.sts.combat.engine.status;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.ArrayList;
import java.util.List;

/**
 * How statuses stack, resist and come apart.
 *
 * <p>These rules used to be spread across the handlers, and they disagreed: applying a status
 * always appended a second copy instead of stacking, so two Poisons ticked twice; removing one
 * always deleted it whole, so the partial removals the data asks for silently became total ones.
 */
public final class StatusOperations {

    /** Statuses that intercept an incoming status instead of modifying it. */
    private static final String ARTIFACT = "Artifact";
    private static final String FILTER = "Filter";
    private static final String UNBIASED = "Unbiased";

    private StatusOperations() {
    }

    /**
     * Outcome of applying a status.
     *
     * @param statuses the holder's resulting list, which can differ even when nothing was applied
     *                 because an interception spends the interceptor
     * @param applied  whether the status got through
     * @param result   the instance now carrying the status, or null when it was intercepted
     * @param stacked  whether it merged into an instance the holder already had
     */
    public record Application(
            List<StatusInstance> statuses,
            boolean applied,
            StatusInstance result,
            boolean stacked
    ) {

        public Application {
            statuses = List.copyOf(statuses);
        }
    }

    /**
     * Applies a status to a holder, honouring the interceptors it carries.
     *
     * <p>Artifact eats an incoming debuff, Filter eats an incoming buff, and both pay an extra
     * charge for a status that resists removal. A source carrying Unbiased ignores all of that.
     */
    public static Application apply(
            List<StatusInstance> holder,
            StatusInstance incoming,
            List<StatusInstance> sourceStatuses
    ) {
        return apply(holder, incoming, sourceStatuses, null);
        }

        public static Application apply(
            List<StatusInstance> holder,
            StatusInstance incoming,
            List<StatusInstance> sourceStatuses,
            CardCatalog catalog
        ) {
        StatusDefinition definition = StatusCatalog.of(incoming.statusType());
        boolean incomingIsDebuff = StatusCatalog.isDebuff(incoming, catalog);
        boolean incomingIsBuff = StatusCatalog.isBuff(incoming, catalog);
        boolean bypassesResistance = incomingIsDebuff
                && sourceStatuses != null
                && sourceStatuses.stream().anyMatch(status -> UNBIASED.equals(status.statusType()));

        List<StatusInstance> statuses = new ArrayList<>(holder);
        if (!bypassesResistance && !StatusCatalog.resistsEverything(incoming)) {
            String interceptor = incomingIsDebuff ? ARTIFACT : incomingIsBuff ? FILTER : null;
            if (interceptor != null) {
                Interception interception = intercept(statuses, interceptor, definition);
                if (interception.blocked()) {
                    return new Application(interception.statuses(), false, null, false);
                }
                statuses = new ArrayList<>(interception.statuses());
            }
        }
        int before = statuses.size();
        StatusInstance merged = insert(statuses, incoming);
        return new Application(statuses, true, merged, statuses.size() == before);
    }

    private record Interception(List<StatusInstance> statuses, boolean blocked) {
    }

    private static Interception intercept(
            List<StatusInstance> statuses,
            String interceptorType,
            StatusDefinition incoming
    ) {
        for (int index = 0; index < statuses.size(); index++) {
            StatusInstance interceptor = statuses.get(index);
            if (!interceptorType.equals(interceptor.statusType()) || interceptor.value() <= 0) {
                continue;
            }
            // A status a card could otherwise strip costs the interceptor an extra charge.
            int charges = incoming.framed() ? 2 : 1;
            int remaining = interceptor.value() - charges;
            List<StatusInstance> updated = new ArrayList<>(statuses);
            if (remaining <= 0) {
                updated.remove(index);
            } else {
                updated.set(index, withValue(interceptor, remaining));
            }
            // Spending more charges than it holds empties the interceptor and lets the status
            // through anyway, so one charge cannot stop a status that costs two.
            return new Interception(updated, remaining >= 0);
        }
        return new Interception(statuses, false);
    }

    /** Unity's own {@code InsertInto} override for these always appends rather than merging:
     *  each instance of Trap or DamageReduction keeps its own numbers, a DelayedStun its own
     *  countdown to a Stun, a Link its own character, and a follow-up its own card and move
     *  restriction, so several authored separately must stay several. */
    private static final java.util.Set<String> NEVER_MERGES = java.util.Set.of(
            "CardFollowUp", "AnyCardFollowUp", "FieldTurnFollowUp",
            "Trap", "DamageReduction", "DelayedStun", "Link", "PowerPassive",
            // Chaque Sape est une instance à part, qui mord pour son compte.
            "Sap");

    /**
     * Stacks a status into a list, merging it with a compatible instance when there is one.
     *
     * <p>Two instances only merge when their durations point the same way: a timed Poison never
     * merges into a permanent one. A timed status adds its turns and keeps the stronger value; a
     * permanent one adds its value, bounded by what the status can hold.
     *
     * @return the instance now carrying the status
     */
    public static StatusInstance insert(List<StatusInstance> statuses, StatusInstance incoming) {
        StatusDefinition definition = StatusCatalog.of(incoming.statusType());
        // La Perte de Force de Pleine lune additionne ce qu'elle doit retirer et repart pour un
        // tour (FullMoonTempEffect.InsertInto) : la fusion ordinaire gardait la plus grande
        // valeur et ajoutait les durées, si bien que deux coups n'en retiraient qu'un, un tour
        // plus tard.
        if ("FullMoonTemp".equals(incoming.statusType())) {
            for (int index = 0; index < statuses.size(); index++) {
                StatusInstance existing = statuses.get(index);
                if (existing.statusType().equals(incoming.statusType())) {
                    StatusInstance merged = new StatusInstance(existing.statusType(),
                            existing.value() + incoming.value(), 1, existing.cardId(),
                            existing.index()).withResistanceOf(existing);
                    statuses.set(index, merged);
                    return merged;
                }
            }
        }
        if (!NEVER_MERGES.contains(incoming.statusType())) {
            for (int index = 0; index < statuses.size(); index++) {
                StatusInstance existing = statuses.get(index);
                if (!existing.statusType().equals(incoming.statusType())
                        || Integer.signum(existing.duration()) != Integer.signum(incoming.duration())) {
                    continue;
                }
                // Unity merges into the instance already carried, so that instance's own
                // removability is what survives the merge.
                StatusInstance merged = (incoming.duration() > 0
                        ? new StatusInstance(
                                existing.statusType(),
                                Math.max(existing.value(), incoming.value()),
                                existing.duration() + incoming.duration(),
                                existing.cardId(),
                                existing.index())
                        : new StatusInstance(
                                existing.statusType(),
                                clamp(existing.value() + incoming.value(), definition.maxValue()),
                                existing.duration(),
                                existing.cardId(),
                                existing.index())).withResistanceOf(existing);
                statuses.set(index, merged);
                return merged;
            }
        }
        StatusInstance added = new StatusInstance(
                incoming.statusType(),
                clamp(incoming.value(), definition.maxValue()),
                incoming.duration(),
                incoming.cardId(),
                incoming.index()).withResistanceOf(incoming);
        statuses.add(added);
        return added;
    }

    /**
     * Outcome of removing a status.
     *
     * @param remaining what stays on the holder, or null when the status is gone
     * @param removed   what came off, which steal and transfer then hand to someone else
     */
    public record Removal(StatusInstance remaining, StatusInstance removed) {
    }

    /**
     * Takes a status off a holder, entirely or in part.
     *
     * <p>{@code carriedPercentage} is how much of the status the removal takes away rather than
     * destroys: at zero the status simply goes, above zero it is split and the holder keeps the
     * remainder. That split is what lets a card steal half a buff instead of all of it.
     */
    public static Removal remove(StatusInstance status, int carriedPercentage) {
        if (carriedPercentage <= 0) {
            return new Removal(null, status);
        }
        int removedValue = ceilPercentage(status.value(), carriedPercentage);
        // A status with no duration keeps having none. Handing back -1 gave the piece that came
        // away a different sign from the one it was cut from, and StatusOperations.insert only
        // merges statuses whose durations share a sign -- so a transferred Strength landed beside
        // the target's own Strength as a second entry instead of adding to it.
        int removedDuration = status.duration() > 0
                ? ceilPercentage(status.duration(), carriedPercentage)
                : status.duration();
        StatusInstance removed = new StatusInstance(
                status.statusType(), removedValue, removedDuration, status.cardId(), status.index());
        StatusInstance remaining = new StatusInstance(
                status.statusType(),
                status.value() - removedValue,
                status.duration() - Math.max(0, removedDuration),
                status.cardId(),
                status.index());

        return new Removal(remaining, removed);
    }

    /**
     * A share of a number, rounded up, in whichever direction the number points.
     *
     * <p>Rounding up has to mean "away from zero", not "towards positive": a debuff whose strength
     * is a negative number -- Strength -2, Dexterity -3 -- is the same magnitude as its positive
     * twin, and 20% of it should come away as -1 the way 20% of +2 comes away as +1. Taking
     * {@code Math.ceil} of the signed value instead rounded -0.4 up to 0, so transferring or
     * stealing a negative-valued status moved a status worth nothing: the target ended up holding
     * "0 Strength", which is neither a buff nor a debuff, while the caster kept the whole of it.
     */
    private static int ceilPercentage(int value, int percentage) {
        int magnitude = (int) Math.ceil(Math.abs((long) value) * percentage / 100.0);
        return value < 0 ? -magnitude : magnitude;
    }

    private static int clamp(int value, int maxValue) {
        return Math.max(-maxValue, Math.min(maxValue, value));
    }

    private static StatusInstance withValue(StatusInstance status, int value) {
        return new StatusInstance(
                status.statusType(), value, status.duration(), status.cardId(), status.index());
    }
}
