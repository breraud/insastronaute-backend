package fr.insastronaute.webapi.sts.combat.model;

import fr.insastronaute.webapi.sts.combat.model.RandomState;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CombatState(
        UUID combatId,
        long revision,
        String dataVersion,
        CombatStatus status,
        String winnerTeamId,
        RandomState random,
        List<CombatantState> combatants,
        List<TimelineEntry> timeline,
        String activeCombatantId,
        CombatCounters counters
) {

    /** A combat saved before the counters existed reloads with an empty tally rather than none. */
    public CombatState(
            UUID combatId,
            long revision,
            String dataVersion,
            CombatStatus status,
            String winnerTeamId,
            RandomState random,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            String activeCombatantId
    ) {
        this(combatId, revision, dataVersion, status, winnerTeamId, random, combatants, timeline,
                activeCombatantId, CombatCounters.initial());
    }

    public CombatState withCounters(CombatCounters counters) {
        return new CombatState(combatId, revision, dataVersion, status, winnerTeamId, random,
                combatants, timeline, activeCombatantId, counters);
    }

    public CombatState {
        Objects.requireNonNull(combatId, "combatId");
        counters = counters == null ? CombatCounters.initial() : counters;
        requireNonBlank(dataVersion, "dataVersion");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(random, "random");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        if (winnerTeamId != null) {
            requireNonBlank(winnerTeamId, "winnerTeamId");
        }
        combatants = List.copyOf(Objects.requireNonNull(combatants, "combatants"));
        timeline = List.copyOf(Objects.requireNonNull(timeline, "timeline"));

        Set<String> combatantIds = new HashSet<>();
        for (CombatantState combatant : combatants) {
            if (!combatantIds.add(combatant.combatantId())) {
                throw new IllegalArgumentException("combatant IDs must be unique");
            }
        }

        Set<String> timelineCombatantIds = new HashSet<>();
        TimelineEntry previous = null;
        for (TimelineEntry entry : timeline) {
            if (!combatantIds.contains(entry.combatantId())) {
                throw new IllegalArgumentException(
                        "timeline must reference existing combatants");
            }
            if (!timelineCombatantIds.add(entry.combatantId())) {
                throw new IllegalArgumentException(
                        "timeline combatant references must be unique");
            }
            if (previous != null && TimelineEntry.CANONICAL_ORDER.compare(previous, entry) > 0) {
                throw new IllegalArgumentException("timeline must use canonical order");
            }
            previous = entry;
        }

        if (activeCombatantId != null) {
            requireNonBlank(activeCombatantId, "activeCombatantId");
        }
        if (status == CombatStatus.ACTIVE) {
            validateActiveState(
                    revision,
                    winnerTeamId,
                    combatants,
                    timeline,
                    timelineCombatantIds,
                    activeCombatantId);
        } else {
            validateFinishedState(winnerTeamId, combatants, activeCombatantId);
        }
    }

    private static void validateActiveState(
            long revision,
            String winnerTeamId,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            Set<String> timelineCombatantIds,
            String activeCombatantId
    ) {
        if (winnerTeamId != null) {
            throw new IllegalArgumentException("active combat must not have a winner");
        }
        Set<String> livingTeams = combatants.stream()
                .filter(combatant -> combatant.hp() > 0)
                .map(CombatantState::teamId)
                .collect(java.util.stream.Collectors.toSet());
        if (livingTeams.size() < 2) {
            throw new IllegalArgumentException(
                    "active combat must have at least two living teams");
        }
        CombatantState activeCombatant = combatants.stream()
                .filter(combatant -> combatant.combatantId().equals(activeCombatantId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "active combatant must exist"));
        if (activeCombatant.hp() <= 0) {
            throw new IllegalArgumentException("active combatant must be living");
        }
        List<TimelineEntry> activeEntries = timeline.stream()
                .filter(entry -> entry.combatantId().equals(activeCombatantId))
                .toList();
        if (activeEntries.size() != 1) {
            throw new IllegalArgumentException(
                    "active combatant must have exactly one timeline entry");
        }
        Set<String> livingCombatantIds = combatants.stream()
                .filter(combatant -> combatant.hp() > 0)
                .map(CombatantState::combatantId)
                .collect(java.util.stream.Collectors.toSet());
        String firstLivingTimelineCombatantId = timeline.stream()
                .map(TimelineEntry::combatantId)
                .filter(livingCombatantIds::contains)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "active combat must have a living timeline entry"));
        if (!activeCombatantId.equals(firstLivingTimelineCombatantId)) {
            throw new IllegalArgumentException(
                    "active combatant must be the first living timeline entry");
        }
        boolean missingLivingCombatant = combatants.stream()
                .anyMatch(combatant -> combatant.hp() > 0
                        && !timelineCombatantIds.contains(combatant.combatantId()));
        if (missingLivingCombatant) {
            throw new IllegalArgumentException(
                    "every living combatant must have a timeline entry");
        }
        if (revision == Long.MAX_VALUE) {
            throw new IllegalArgumentException("active combat revision cannot advance");
        }
        if (timeline.stream().anyMatch(entry -> entry.sequence() == Long.MAX_VALUE)) {
            throw new IllegalArgumentException("active combat timeline sequence cannot advance");
        }
        TimelineEntry activeEntry = activeEntries.getFirst();
        if (activeEntry.readyAtTick() > Long.MAX_VALUE - activeCombatant.turnDelayTicks()) {
            throw new IllegalArgumentException("active combat timeline tick cannot advance");
        }
    }

    private static void validateFinishedState(
            String winnerTeamId,
            List<CombatantState> combatants,
            String activeCombatantId
    ) {
        if (activeCombatantId != null) {
            throw new IllegalArgumentException(
                    "finished combat must not have an active combatant");
        }
        Set<String> livingTeams = combatants.stream()
                .filter(combatant -> combatant.hp() > 0)
                .map(CombatantState::teamId)
                .collect(java.util.stream.Collectors.toSet());
        if (livingTeams.size() > 1) {
            throw new IllegalArgumentException(
                    "finished combat must not have multiple living teams");
        }
        String expectedWinner = livingTeams.size() == 1
                ? livingTeams.iterator().next()
                : null;
        if (!Objects.equals(winnerTeamId, expectedWinner)) {
            throw new IllegalArgumentException(
                    "winner must match the only living team, or be null for a draw");
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
