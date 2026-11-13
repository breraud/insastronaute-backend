package fr.insastronaute.webapi.sts.combat.engine;

import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.Comparator;
import java.util.List;

public final class TimelineOperations {

    static final Comparator<TimelineEntry> ORDER = TimelineEntry.CANONICAL_ORDER;

    private TimelineOperations() {
    }

    /**
     * The timeline in the order the state requires.
     *
     * <p>A timeline already in order is handed back as it came, which is the common case and what
     * callers rely on when they check that a turn nothing touched came through untouched. Sorting
     * only happens once something has actually moved a turn.
     */
    public static List<TimelineEntry> sorted(List<TimelineEntry> timeline) {
        return isSorted(timeline) ? timeline : timeline.stream().sorted(ORDER).toList();
    }

    private static boolean isSorted(List<TimelineEntry> timeline) {
        for (int index = 1; index < timeline.size(); index++) {
            if (ORDER.compare(timeline.get(index - 1), timeline.get(index)) > 0) {
                return false;
            }
        }
        return true;
    }
}
