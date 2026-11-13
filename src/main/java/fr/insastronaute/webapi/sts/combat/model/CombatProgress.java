package fr.insastronaute.webapi.sts.combat.model;

import fr.insastronaute.webapi.sts.combat.engine.TimelineOperations;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Where a combat stands once a revision has landed: whether it is over, who won, and
 * the timeline that goes with that answer.
 *
 * <p>This lives beside {@link CombatState} because {@link CombatState} already asks
 * these very questions of every state it is handed — two living teams, and an active
 * combatant that is the first living timeline entry. The engine used to derive the
 * same answers on its own, in four places, and six production lockups came out of the
 * four drifting apart. The rule is stated once here, and the state checks it there.
 */
public record CombatProgress(
        boolean finished,
        String winnerTeamId,
        List<TimelineEntry> timeline
) {

    public CombatProgress {
        timeline = List.copyOf(timeline);
    }

    /**
     * Reads the standing of a combat from the combatants and timeline a revision leaves
     * behind.
     *
     * <p>The dead leave the timeline of a combat that is over, so a finished state can be
     * built without them; while it runs they keep their place, since the state only ever
     * asks that the living be present. The timeline comes back sorted whatever happened:
     * the handlers that move a turn rewrite a tick where its entry already sits, so any
     * of them can leave the list out of the order the state requires.
     */
    public static CombatProgress of(
            List<CombatantState> combatants,
            List<TimelineEntry> timeline
    ) {
        Set<String> livingTeams = new HashSet<>();
        Set<String> living = new HashSet<>();
        for (CombatantState combatant : combatants) {
            if (combatant.hp() > 0) {
                livingTeams.add(combatant.teamId());
                living.add(combatant.combatantId());
            }
        }
        boolean finished = livingTeams.size() < 2;
        String winnerTeamId = livingTeams.size() == 1
                ? livingTeams.iterator().next()
                : null;
        return new CombatProgress(
                finished,
                winnerTeamId,
                TimelineOperations.sorted(finished
                        ? timeline.stream()
                                .filter(entry -> living.contains(entry.combatantId()))
                                .toList()
                        : timeline));
    }

    /**
     * Whose turn it is now.
     *
     * <p>The combatant a handler would like to keep acting, when it is still alive —
     * otherwise the first entry on the timeline that belongs to somebody alive. A
     * combatant killed by its own statuses, or by its own card, keeps the place it was
     * just given back and sits ahead of everybody; handing it the turn is what stuck a
     * player in production.
     *
     * @param preferred   the combatant the caller would keep acting, or null to hand over
     * @param combatants  the combatants as this revision leaves them
     * @throws IllegalStateException when nobody on the timeline is alive, which is a
     *                               broken state rather than a finished combat
     */
    public String nextActiveCombatantId(String preferred, List<CombatantState> combatants) {
        Set<String> living = new HashSet<>();
        for (CombatantState combatant : combatants) {
            if (combatant.hp() > 0) {
                living.add(combatant.combatantId());
            }
        }
        if (preferred != null && living.contains(preferred)) {
            return preferred;
        }
        return timeline.stream()
                .map(TimelineEntry::combatantId)
                .filter(living::contains)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "active combat requires a living timeline entry"));
    }
}
