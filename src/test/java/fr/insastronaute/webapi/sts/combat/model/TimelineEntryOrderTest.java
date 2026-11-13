package fr.insastronaute.webapi.sts.combat.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TimelineEntryOrderTest {

    /// The order a combat's timeline is kept in, stated once so that the list cannot be
    /// sorted by one rule and judged by another.
    @Test
    void ordersByTickThenBySequence() {
        List<TimelineEntry> sorted = List.of(
                new TimelineEntry("c", 20, 0),
                new TimelineEntry("a", 10, 1),
                new TimelineEntry("b", 10, 0))
                .stream().sorted(TimelineEntry.CANONICAL_ORDER).toList();

        assertThat(sorted).extracting(TimelineEntry::combatantId)
                .containsExactly("b", "a", "c");
    }

    /// When tick and sequence tie, the combatant id is the only thing left to decide
    /// the order — and it has to, or two states could agree on everything but who
    /// goes first.
    @Test
    void breaksATieOnTickAndSequenceByCombatantId() {
        List<TimelineEntry> sorted = List.of(
                new TimelineEntry("z", 10, 0),
                new TimelineEntry("a", 10, 0))
                .stream().sorted(TimelineEntry.CANONICAL_ORDER).toList();

        assertThat(sorted).extracting(TimelineEntry::combatantId)
                .containsExactly("a", "z");
    }
}
