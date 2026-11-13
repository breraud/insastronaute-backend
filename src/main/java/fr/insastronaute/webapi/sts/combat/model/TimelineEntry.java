package fr.insastronaute.webapi.sts.combat.model;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

public record TimelineEntry(String combatantId, long readyAtTick, long sequence, long pendingSelfDelayTicks) {

    /**
     * The order a combat's timeline is kept in.
     *
     * <p>It lives here because both the sorting and the judging need it, and it used to
     * exist twice — once in {@code CombatState} to validate, once in
     * {@code TimelineOperations} to sort. Two copies of one rule are what the engine
     * spent a day removing.</p>
     */
    public static final Comparator<TimelineEntry> CANONICAL_ORDER =
            Comparator.comparingLong(TimelineEntry::readyAtTick)
                    .thenComparingLong(TimelineEntry::sequence)
                    .thenComparing(TimelineEntry::combatantId);

    public TimelineEntry {
        if (combatantId == null || combatantId.isBlank()) {
            throw new IllegalArgumentException("combatantId must not be blank");
        }
        if (readyAtTick < 0) {
            throw new IllegalArgumentException("readyAtTick must not be negative");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
    }

    /** A freshly scheduled entry, owing nothing yet to a turn manipulation still in flight. */
    public TimelineEntry(String combatantId, long readyAtTick, long sequence) {
        this(combatantId, readyAtTick, sequence, 0L);
    }

    /**
     * The same entry, with {@code amount} added to what its holder's own AdvanceTurn/DelayTurn
     * effects owe its next turn. {@code readyAtTick} is left untouched: while an entry is the
     * one currently taking its turn, that tick is the turn under way, not the one being asked
     * for, and only {@link fr.insastronaute.webapi.sts.combat.engine.CombatEngine#handle} folds
     * this back in, once the turn actually ends.
     */
    public TimelineEntry withPendingSelfDelay(long amount) {
        return new TimelineEntry(combatantId, readyAtTick, sequence, pendingSelfDelayTicks + amount);
    }

    /**
     * Whether {@code combatantId} is the one whose turn is happening right now: the earliest
     * tick among every combatant still standing. A card that advances or delays its own caster
     * must tell the two apart — the caster's entry here is the turn under way, and only a
     * <em>different</em>, not-yet-active entry may have its {@code readyAtTick} moved outright.
     */
    public static boolean isActiveNow(String combatantId, List<TimelineEntry> timeline, Set<String> livingCombatantIds) {
        long minReadyAtTick = Long.MAX_VALUE;
        for (TimelineEntry entry : timeline) {
            if (livingCombatantIds.contains(entry.combatantId())) {
                minReadyAtTick = Math.min(minReadyAtTick, entry.readyAtTick());
            }
        }
        for (TimelineEntry entry : timeline) {
            if (entry.combatantId().equals(combatantId)) {
                return entry.readyAtTick() == minReadyAtTick;
            }
        }
        return false;
    }
}
