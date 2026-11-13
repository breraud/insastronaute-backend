package fr.insastronaute.webapi.sts.multiplayer.combat;

import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatCounters;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The combat as one player is allowed to see it.
 *
 * <p>A PvP {@link CombatState} holds both players' private information: every combatant's
 * {@code piles}, and the {@code random} state, a deterministic SplitMix64 seed from which the
 * entire future draw order can be computed. Handing that state to a player would give them the
 * opponent's hand and every card either side is about to draw, and no server-side rule could
 * detect the abuse afterwards.
 *
 * <p>So nothing sends a {@code CombatState} to a player: every player is served the projection
 * this class builds for them. The viewer's own combatant keeps its piles in full; every other
 * combatant is reduced to what the table can legitimately see — the sizes of its draw pile and
 * hand, plus its discard and exhaust piles, which are face-up in this game. The RNG state has no
 * field to travel in at all, which is why it is left out of the record rather than nulled.
 *
 * <p>The counters do travel, whole. They are what cards scale on — one more damage per energy
 * spent this turn, an effect gated on the card not having been played yet this turn — so a client
 * without them cannot show what a card in the viewer's hand is about to do, and a PvP deck draws
 * from the same catalog as a PvE one, where the client already reads them. Nothing in them is
 * private either: they count cards played face up, energy spent in the open and hp lost in plain
 * sight, all of it already derivable from what this view shows.
 *
 * <p>{@code playedHistory} travels whole, and to both players unchanged, because it passes the
 * same test: a card that has been played is public. Every {@code CardPlayed} already reaches both
 * players unfiltered — {@link StsPvpCombatBroadcaster#visibleTo} drops only what names a card in a
 * pile the viewer cannot see, and a played card is in none — so the history says nothing the table
 * has not already seen. It is served rather than left to each client to accumulate because a
 * client's own tally does not survive a reconnection: the snapshot carries a state, never the
 * events that led to it, so a reconnected player would face an empty « shared » history.
 *
 * <p>{@code turnDeadline} and {@code serverTime} travel for the same reason, and the test is the
 * one this class exists to apply: neither is derived from anybody's piles or from {@code random}.
 * The deadline is a property of the battle, not of a player — it is the same instant for both
 * sides, both of them already know a turn lasts 30 seconds, and knowing when the current one ends
 * tells nothing about a hand, a draw order or a seed. {@code serverTime} is the clock the deadline
 * is expressed against; without it a client counts down against its own clock and is wrong by
 * exactly its drift. Withholding either would hide from a player only the passing of their own
 * turn, which is not what the projection is for.
 */
public record StsPvpCombatView(
        UUID combatId,
        long revision,
        String dataVersion,
        CombatStatus status,
        String winnerTeamId,
        String activeCombatantId,
        Instant turnDeadline,
        Instant serverTime,
        List<TimelineEntry> timeline,
        List<StsPvpCombatantView> combatants,
        CombatCounters counters,
        List<StsPvpCardHistory.Entry> playedHistory
) {

    public StsPvpCombatView {
        Objects.requireNonNull(combatId, "combatId");
        timeline = List.copyOf(Objects.requireNonNull(timeline, "timeline"));
        combatants = List.copyOf(Objects.requireNonNull(combatants, "combatants"));
        playedHistory = playedHistory == null ? List.of() : List.copyOf(playedHistory);
    }

    /**
     * An opponent's piles, as much of them as the viewer is entitled to.
     *
     * <p>Draw and hand collapse to a count: their contents are hidden, and so is their
     * <em>order</em>, which is what would give away the opponent's next draws. Discard and
     * exhaust are face-up information in this game and travel whole.
     */
    public record StsPvpOpponentPiles(
            int drawCount,
            int handCount,
            List<CardInstance> discard,
            List<CardInstance> exhaust
    ) {

        public StsPvpOpponentPiles {
            discard = List.copyOf(Objects.requireNonNull(discard, "discard"));
            exhaust = List.copyOf(Objects.requireNonNull(exhaust, "exhaust"));
        }
    }

    /**
     * One combatant, seen by one viewer.
     *
     * <p>Exactly one of {@code piles} (the viewer's own, in full) and {@code hiddenPiles} (an
     * opponent's, counts only) is non-null, so a client cannot read private cards off a combatant
     * it does not own even by accident.
     */
    public record StsPvpCombatantView(
            String combatantId,
            String teamId,
            int maxHp,
            int hp,
            int armor,
            int energy,
            int baseEnergy,
            List<StatusInstance> statuses,
            CardPiles piles,
            StsPvpOpponentPiles hiddenPiles
    ) {

        public StsPvpCombatantView {
            statuses = statuses == null ? List.of() : List.copyOf(statuses);
            if ((piles == null) == (hiddenPiles == null)) {
                throw new IllegalArgumentException(
                        "a combatant view carries either its own piles or an opponent's counts");
            }
        }
    }

    /**
     * @param state             the authoritative combat state, never sent anywhere as it is
     * @param viewerCombatantId the combatant reading it, i.e. the player's own combatant id
     * @param turnDeadline      when the current turn is dropped for want of an answer, or null
     *                          while no turn is open; the same instant for both players
     * @param serverTime        the server's own clock, so the reader counts down against the
     *                          clock the deadline was written on rather than against its own
     * @implNote this overload serves an empty history; it exists for callers that have no battle
     *           at hand, and every caller that does should pass the battle's own
     * @return the same combat with everything {@code viewerCombatantId} may not see removed
     * @throws IllegalArgumentException when no combatant of {@code state} is the viewer, because
     *         a projection for a stranger is a question with no safe answer
     */
    public static StsPvpCombatView forViewer(
            CombatState state,
            String viewerCombatantId,
            Instant turnDeadline,
            Instant serverTime) {
        return forViewer(state, viewerCombatantId, turnDeadline, serverTime, List.of());
    }

    /**
     * @param playedHistory the duel's shared card history, served to every viewer alike
     */
    public static StsPvpCombatView forViewer(
            CombatState state,
            String viewerCombatantId,
            Instant turnDeadline,
            Instant serverTime,
            List<StsPvpCardHistory.Entry> playedHistory) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(viewerCombatantId, "viewerCombatantId");

        boolean viewerIsInCombat = state.combatants().stream()
                .anyMatch(combatant -> combatant.combatantId().equals(viewerCombatantId));
        if (!viewerIsInCombat) {
            throw new IllegalArgumentException(
                    "combatant " + viewerCombatantId + " does not take part in this combat");
        }

        List<StsPvpCombatantView> combatants = state.combatants().stream()
                .map(combatant -> combatant.combatantId().equals(viewerCombatantId)
                        ? own(combatant)
                        : opponent(combatant))
                .toList();

        return new StsPvpCombatView(
                state.combatId(),
                state.revision(),
                state.dataVersion(),
                state.status(),
                state.winnerTeamId(),
                state.activeCombatantId(),
                turnDeadline,
                serverTime,
                state.timeline(),
                combatants,
                state.counters(),
                playedHistory);
    }

    private static StsPvpCombatantView own(CombatantState combatant) {
        return combatantView(combatant, combatant.piles(), null);
    }

    private static StsPvpCombatantView opponent(CombatantState combatant) {
        CardPiles piles = combatant.piles();
        return combatantView(combatant, null, new StsPvpOpponentPiles(
                piles.draw().size(),
                piles.hand().size(),
                piles.discard(),
                piles.exhaust()));
    }

    private static StsPvpCombatantView combatantView(
            CombatantState combatant,
            CardPiles piles,
            StsPvpOpponentPiles hiddenPiles
    ) {
        return new StsPvpCombatantView(
                combatant.combatantId(),
                combatant.teamId(),
                combatant.maxHp(),
                combatant.hp(),
                combatant.armor(),
                combatant.energy(),
                combatant.baseEnergy(),
                combatant.statuses(),
                piles,
                hiddenPiles);
    }
}
