package fr.insastronaute.webapi.sts.combat.engine;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardModifier;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.CombatCommand;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.command.RejectionCode;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.engine.selection.CardSelectionCandidate;
import fr.insastronaute.webapi.sts.combat.engine.selection.CardSelectionResolver;
import fr.insastronaute.webapi.sts.combat.event.CardMoved;
import fr.insastronaute.webapi.sts.combat.event.CardPlayed;
import fr.insastronaute.webapi.sts.combat.event.CombatEnded;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.EnergySpent;
import fr.insastronaute.webapi.sts.combat.event.TurnEnded;
import fr.insastronaute.webapi.sts.combat.event.TurnStarted;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatCounters;
import fr.insastronaute.webapi.sts.combat.model.CombatProgress;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import fr.insastronaute.webapi.sts.combat.model.modifier.StatType;
import fr.insastronaute.webapi.sts.combat.model.modifier.ModifierKind;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CombatEngine {

    private static final int HAND_SIZE = 5;
    private static final String HAND_PILE = "HAND";
    private static final String DISCARD_PILE = "DISCARD";
    private static final String EXHAUST_PILE = "EXHAUST";
    private static final String COST_NULLIFY = "CostNullify";
    private static final String VIGOR = "Vigor";
    private static final String CRITICAL = "Critical";
    private static final String TIME_DISTORTION = "TimeDistortion";

    /** Le plafond, en pourcent du délai de la victime, de ce qu'une distorsion repousse. */
    private static final int MAX_TIME_DISTORTION_PERCENT = 50;
    private static final String NO_PILE = "NONE";
    private static final String FORCED_ACTION = "ForcedAction";
    private static final String IMITATE = "Imitate";
    /** The extra attack Sadism III sets off (SadismIIIStatus.cs). */
    private static final String SADISM_III_ATTACK = "Acharnement";

    /**
     * A hard stop on the stun-skip loop below, kept far out of reach of any real combat. The
     * loop's own progress check is what catches a Stun that can never resolve, by watching a
     * skipped combatant's own statuses for a skip that changed nothing at all. That proof rests
     * on one assumption: that a combatant's stun-skip processing depends on nothing but its own
     * statuses, evolving independently of everything else. Nothing enforces that assumption
     * today, and a future status or relic could break it without anyone noticing here — the
     * failure mode of a broken proof is a request thread that spins forever, silent, with no
     * exception and no revision, which is worse than anything two days of this reform fixed.
     * This cap exists only to turn that into a loud failure instead. {@code StatusOperations}
     * stacks matching timed statuses by adding their durations with no upper clamp, so a Stun's
     * duration is not itself bounded by the catalog, but ten thousand skips is far beyond
     * anything any card, relic or chain of them could plausibly stack onto one combatant in one
     * combat — and cheap enough, in plain list and map operations, to still return in well under
     * a second if this is ever the one that fires.
     */
    private static final int MAX_STUN_SKIPS = 10_000;

    private final CardCatalog catalog;
    private final RelicEngine relicEngine;
    private final EffectResolver effectResolver;
    private final fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentEngine enchantmentEngine;
        private final PowerPassiveEngine powerPassiveEngine;

    public CombatEngine(CardCatalog catalog, RelicEngine relicEngine) {
        this(catalog, relicEngine, new fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentEngine(java.util.List.of()));
    }

    public CombatEngine(CardCatalog catalog, RelicEngine relicEngine, fr.insastronaute.webapi.sts.combat.engine.enchantment.EnchantmentEngine enchantmentEngine) {
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
        this.relicEngine = Objects.requireNonNull(relicEngine, "relicEngine must not be null");
        this.enchantmentEngine = Objects.requireNonNull(enchantmentEngine, "enchantmentEngine must not be null");
        this.effectResolver = new EffectResolver(catalog, relicEngine, enchantmentEngine);
        this.powerPassiveEngine = new PowerPassiveEngine(catalog);
    }

    /**
     * Validates a trusted internal domain command against the current combat state.
     *
     * <p>Transport adapters must not pass network DTOs directly. Before calling this method, an
     * orchestrator must derive {@code actorId} from the authenticated principal and verify its
     * combat assignment. Authentication intentionally remains outside this pure domain engine.
     *
     * @param state current combat state
     * @param command trusted command assembled by the orchestrator
     * @return accepted state advance or contextual command rejection
     */
    /** The catalog this engine judges cards against, for a caller that must read the same one. */
    public CardCatalog catalog() {
        return catalog;
    }

    /**
     * Le Désespoir, quand ce combat le connaît.
     *
     * <p>Rendre une carte dont le catalogue n'a pas la définition serait pire que la main vide
     * qu'elle console : tout ce qui lit une main en attend une, et s'arrête sur celle qui n'en a
     * pas. Un jeu de données sans Désespoir laisse donc simplement la main vide.
     *
     * <p>Et seulement à un joueur : c'est la main humaine que le Désespoir console (Unity le
     * donne depuis le deck du joueur). Un ennemi joue un schéma, pas une main, et sa pile vide
     * lui vaudrait un Désespoir à chaque tour -- que sa politique de jeu finirait par jouer.
     */
    private String despairCardId(CombatantState drawer) {
        if (drawer == null
                || drawer.controllerType() != fr.insastronaute.webapi.sts.combat.model.ControllerType.HUMAN
                || catalog.find(PileOperations.DESPAIR_CARD_ID) == null) {
            return null;
        }
        return PileOperations.DESPAIR_CARD_ID;
    }

    public CombatResult handle(CombatState state, CombatCommand command) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(command, "command must not be null");

        if (!state.combatId().equals(command.combatId())) {
            return reject(
                    state,
                    RejectionCode.COMBAT_NOT_FOUND_OR_MISMATCH,
                    "Command combat identifier does not match the loaded combat");
        }
        if (state.revision() != command.expectedRevision()) {
            return reject(
                    state,
                    RejectionCode.STALE_REVISION,
                    "Expected revision does not match current revision " + state.revision());
        }
        if (!catalog.dataVersion().equals(state.dataVersion())) {
            return reject(
                    state,
                    RejectionCode.DATA_VERSION_MISMATCH,
                    "Combat data version does not match loaded catalog version");
        }
        if (state.status() != CombatStatus.ACTIVE) {
            return reject(
                    state,
                    RejectionCode.COMBAT_FINISHED,
                    "Combat is already finished");
        }

        CombatantState actor = findCombatant(state, command.actorId());
        if (!command.actorId().equals(state.activeCombatantId())
                || actor == null
                || actor.hp() <= 0) {
            return reject(
                    state,
                    RejectionCode.NOT_ACTOR_TURN,
                    "Actor is not the living active combatant");
        }

        if (command instanceof PlayCardCommand playCard) {
            CombatResult rejection = validatePlayCard(state, actor, playCard);
            if (rejection != null) {
                return rejection;
            }
            return playCard(state, actor, playCard);
        }
        if (command instanceof fr.insastronaute.webapi.sts.combat.command.AiPlayPatternCommand playPattern) {
            return playAiPattern(state, actor, playPattern);
        }
        if (command instanceof EndTurnCommand) {
            return endTurn(state, actor);
        }
        throw new IllegalStateException("unsupported validated combat command");
    }

    private CombatResult endTurn(CombatState state, CombatantState endingActor) {
        long resultingRevision = Math.incrementExact(state.revision());
        TimelineEntry activeEntry = requireUniqueActiveTimelineEntry(state);
        // The wait goes through the calculator, which is what lets Haste, Slow and Speed shorten
        // or stretch it. Adding the authored delay raw left the whole tempo family inert.
        // pendingSelfDelayTicks folds in whatever this turn's own AdvanceTurn/DelayTurn effects
        // owed it: those never moved activeEntry.readyAtTick() directly while it was under way,
        // precisely so the turn under way itself never moved -- only what follows it here does.
        long calculatedNextReadyAtTick = Math.max(0, Math.addExact(
                Math.addExact(
                        activeEntry.readyAtTick(),
                        Math.max(0, BattleCalculator.getModifiedValue(
                                endingActor.turnDelayTicks(),
                                StatType.TURN_DELAY,
                                new EffectContext(
                                        state, endingActor, null, List.of(), null, false, List.of(),
                                        state.timeline(), state.combatants())))),
                activeEntry.pendingSelfDelayTicks()));
        long nextReadyAtTick = Math.max(
                Math.incrementExact(activeEntry.readyAtTick()),
                calculatedNextReadyAtTick);
        long nextSequence = Math.incrementExact(state.timeline().stream()
                .mapToLong(TimelineEntry::sequence)
                .max()
                .orElseThrow(() -> new IllegalStateException("active timeline is empty")));

        List<CombatEvent> events = new ArrayList<>();

        fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep closed = closeTheTurnOf(
                endingActor.combatantId(),
                state,
                resultingRevision,
                state.combatants(),
                state.timeline(),
                state.random(),
                events);
        List<CombatantState> combatants = closed.combatants();
        RandomState random = closed.random();
        StatusEventReactionResult statusEventReactions = applyStatusEventReactions(
                state.combatId(), resultingRevision, combatants, events, 0);
        combatants = statusEventReactions.combatants();
        events.addAll(statusEventReactions.events());
        nextReadyAtTick = Math.addExact(nextReadyAtTick,
                compilationDelayTicks(combatants, state.timeline(), endingActor.combatantId(), events, 0));

        events.add(new TurnEnded(
                state.combatId(),
                resultingRevision,
                endingActor.combatantId(),
                activeEntry.readyAtTick(),
                nextReadyAtTick));
        PowerPassiveEngine.Result turnEndPowers = powerPassiveEngine.onTurnEnded(
                state.combatId(), resultingRevision, endingActor.combatantId(), combatants);
        combatants = turnEndPowers.combatants();
        events.addAll(turnEndPowers.events());

        // La file telle que la fermeture du tour la laisse : une carte qui se joue d'elle-même
        // peut y toucher, et on repartait de celle de l'état, qui ne le savait pas.
        List<TimelineEntry> timeline = advanceTimeline(
                state,
                closed.timeline(),
                activeEntry,
                nextReadyAtTick,
                nextSequence);

        // A turn that closes on a death closes the combat with it. The question is asked here, and
        // not only once the next turn has been opened, so that nothing opens inside a combat
        // already decided: the armour reset, the draw and the statuses of a turn that should never
        // have begun could kill again and turn a won combat into a draw.
        CombatProgress afterTurnEnd = outcomeOf(combatants, timeline);
        if (afterTurnEnd.finished()) {
            return transition(
                    state,
                    resultingRevision,
                    random,
                    combatants,
                    timeline,
                    null,
                    events,
                    state.counters().observing(events).atTurnEnd(endingActor.combatantId()));
        }

        return handTurnOn(
                state,
                resultingRevision,
                combatants,
                timeline,
                random,
                events,
                0,
                state.counters(),
                endingActor.combatantId());
    }

    /**
     * Runs a turn's ending ceremony: its end-of-turn statuses, the end-of-turn relic hook, and
     * its hand discarded.
     *
     * <p>This is the segment {@link #endTurn} always ran for the actor that closed its own turn,
     * pulled out so a caster that loses the hand mid-action while still standing can run through
     * it too, and end its turn like anybody else instead of skipping straight to the hand-over
     * with a full hand and its statuses never having ticked. A caster killed by its own card does
     * not call this: there is no hook for dying, so nothing is waiting on that passage, and its
     * turn was cut short rather than closed.
     *
     * <p>The timeline comes back as the ceremony leaves it: a card that plays itself as the turn
     * closes can reschedule somebody, like any other card. Only {@link #endTurn} advances the
     * timeline for the turn it closes.
     *
     * @param actorId whose turn this closes
     * @param events  the caller's own event list, appended to in place
     */
    private fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep closeTheTurnOf(
            String actorId,
            CombatState state,
            long resultingRevision,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            List<CombatEvent> events
    ) {
        CombatantState endingActor = combatants.stream()
                .filter(combatant -> combatant.combatantId().equals(actorId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "turn closed for an unknown combatant"));

        StatusEngine.StatusTickResult endTick = StatusEngine.processTurnEnd(
                endingActor, resultingRevision, state.combatId(), random);
        endingActor = endTick.combatant();
        events.addAll(endTick.events());
        if (state.counters().attacksPlayedThisTurn() == 0 && endingActor.statuses().stream()
                .anyMatch(status -> "Innocence".equals(status.statusType()))) {
            List<StatusInstance> statuses = new ArrayList<>(endingActor.statuses());
            StatusInstance applied = fr.insastronaute.webapi.sts.combat.engine.status.StatusOperations.insert(
                    statuses, new StatusInstance("DamageReduction", 60, 1, "", 0));
            endingActor = withStatuses(endingActor, statuses);
            events.add(new fr.insastronaute.webapi.sts.combat.event.StatusApplied(
                    state.combatId(), resultingRevision, actorId, actorId,
                    applied.statusType(), applied.value(), applied.duration(), true));
        }
        RandomState nextRandom = endTick.random();

        List<CombatantState> rosterForArm = replaceCombatant(
                combatants, endingActor.combatantId(), endingActor);
        MechaArmed mechaArmed = applyMechaArm(
                state.combatId(), resultingRevision, rosterForArm, endingActor, nextRandom, events);
        rosterForArm = mechaArmed.combatants();
        endingActor = findCombatant(rosterForArm, endingActor.combatantId());
        nextRandom = mechaArmed.random();

        endingActor = relicEngine.processTurnEnd(endingActor, state, events);
        rosterForArm = replaceCombatant(rosterForArm, endingActor.combatantId(), endingActor);

        // Les cartes qui se jouent d'elles-mêmes le font ici, avant que la main ne parte à la
        // défausse qui les emporterait.
        MechanicalPlays mechanical = playMechanicalCards(
                state, resultingRevision, rosterForArm, state.timeline(),
                endingActor.combatantId(), nextRandom, events);
        rosterForArm = mechanical.combatants();
        CombatantState playedItself = findCombatant(rosterForArm, endingActor.combatantId());
        if (playedItself != null) {
            endingActor = playedItself;
        }
        nextRandom = mechanical.random();

        CardPiles discardedPiles = discardHand(
                state, resultingRevision, endingActor, events);
        CombatantState discardedActor = withEnergyPilesAndArmor(
                endingActor, endingActor.energy(), discardedPiles, endingActor.armor());
        List<CombatantState> updatedCombatants = replaceCombatant(
                rosterForArm, endingActor.combatantId(), discardedActor);
        // Et une qui tue en le fermant : Brûlure mord à la fin du tour.
        updatedCombatants = answerAStatusDeath(
                findCombatant(combatants, endingActor.combatantId()), discardedActor,
                updatedCombatants, timeline, nextRandom, state.combatId(), resultingRevision,
                events);

        // A field turn just ended for everybody watching, and this one's own turn ended for it,
        // mirroring Character.EndTurn -> CombatManager.FieldTurnEnd. The relics that answer the
        // field's clock rather than their own bearer's are asked here, for the same reason and at
        // the same moment as the follow-ups below.
        updatedCombatants = relicEngine.processFieldTurnEnd(
                state, endingActor.combatantId(), updatedCombatants, events);

        FollowUpResolution fieldTurnFollowUps = applyFieldTurnEndFollowUps(
                state.combatId(), resultingRevision, updatedCombatants, timeline, nextRandom,
                endingActor.combatantId());
        events.addAll(fieldTurnFollowUps.events());
        FollowUpResolution propulsion = applyPropulsion(
                state.combatId(), resultingRevision, fieldTurnFollowUps.combatants(),
                fieldTurnFollowUps.timeline(), fieldTurnFollowUps.random());
        events.addAll(propulsion.events());

        return new fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep(
                propulsion.combatants(), propulsion.timeline(), events, propulsion.random());
    }

    /**
     * Announces the same {@code TurnEnded} {@link #endTurn} always announces, for a caster that
     * closed its turn through {@link #closeTheTurnOf} instead — but only when there is a real
     * tick to report.
     *
     * <p>A caster that loses the hand while alive because a DelayTurn moved its own place has a
     * genuine next tick: the effect that cost it the turn is the same one that rescheduled it, so
     * {@code state.timeline()} (before the card resolved) and {@code timeline} (after) disagree
     * on where it sits. That is fidelity {@code endTurn} has too, just computed differently — it
     * schedules a fresh tick through the delay stat, this reads the one a card already wrote.
     *
     * <p>Losing the turn does not always mean the caster's own entry moved, though: in principle
     * something else could be scheduled ahead of it without its own tick changing at all, and
     * {@link TurnEnded} refuses a tick that is not strictly later than the one before — rightly,
     * since inventing one would misreport when the caster is next due. When that happens, no
     * event is appended here; the new active combatant and the reordered timeline already in the
     * revision are what the client reads instead.
     */
    private static void appendTurnEndedIfRescheduled(
            CombatState state,
            long resultingRevision,
            String actorId,
            List<TimelineEntry> timeline,
            List<CombatEvent> events
    ) {
        long previousReadyAtTick = readyAtTickOf(state.timeline(), actorId);
        long nextReadyAtTick = readyAtTickOf(timeline, actorId);
        if (nextReadyAtTick > previousReadyAtTick) {
            events.add(new TurnEnded(
                    state.combatId(), resultingRevision, actorId,
                    previousReadyAtTick, nextReadyAtTick));
        }
    }

    private static long readyAtTickOf(List<TimelineEntry> timeline, String combatantId) {
        return timeline.stream()
                .filter(entry -> entry.combatantId().equals(combatantId))
                .mapToLong(TimelineEntry::readyAtTick)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "timeline entry missing for " + combatantId));
    }

    /**
     * Takes the turn away from a caster that can no longer hold it and gives it to the next living
     * entry, closing the turn it lost on the way.
     *
     * <p>Written once because it was first written twice. Both card handlers carried their own copy
     * of this ceremony, and the guard that keeps a turn from opening inside a combat the closing
     * ceremony has already decided sat in {@code endTurn}'s copy and in neither of theirs. The
     * callers differ only in where they read their state from, which is all the parameters are.
     *
     * <p>A caster that lost the hand while alive ends its turn like anybody else: its statuses tick
     * and its hand is discarded, or delaying yourself would have become a hidden way to hold cards
     * over. One killed by its own card keeps its exemption instead — there is no hook for dying, so
     * nothing is waiting on that passage.
     */
    private CombatResult handOverTheLostTurn(
            CombatState state,
            long resultingRevision,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            CombatCounters counters,
            List<CombatEvent> events
    ) {
        boolean casterLives = isStanding(combatants, state.activeCombatantId());
        List<CombatantState> handOverCombatants = combatants;
        List<TimelineEntry> handOverTimeline = timeline;
        RandomState handOverRandom = random;
        CombatCounters handOverCounters = counters;

        if (casterLives) {
            int closingStart = events.size();
            var closed = closeTheTurnOf(
                    state.activeCombatantId(),
                    state,
                    resultingRevision,
                    handOverCombatants,
                    handOverTimeline,
                    handOverRandom,
                    events);
            handOverCombatants = closed.combatants();
            handOverTimeline = closed.timeline();
            handOverRandom = closed.random();
            appendTurnEndedIfRescheduled(
                    state, resultingRevision, state.activeCombatantId(), handOverTimeline, events);

            // closeTheTurnOf's own contribution never reached `counters`: it was computed
            // before this branch ran, and handTurnOn's own fold starts only after this
            // closing ceremony, to avoid folding it twice. Folded once, here, instead.
            handOverCounters = counters.observing(events.subList(closingStart, events.size()));

            // The closing ceremony can kill the caster it just closed: an end-of-turn Burn
            // finishes off somebody who was still standing when the card resolved. The combat
            // is over where that happens, and the question is asked here rather than left to
            // the hand-over, which asks it only once a turn has already been opened — armour
            // cleared, a hand dealt and statuses run for a combatant that should never have
            // acted, and able to kill again and hand a won combat back as a draw. This is the
            // same guard endTurn carries after its own closing ceremony, for the same reason:
            // nothing opens inside a combat already decided.
            CombatProgress afterClosing = outcomeOf(handOverCombatants, handOverTimeline);
            if (afterClosing.finished()) {
                return transition(
                        state,
                        resultingRevision,
                        handOverRandom,
                        handOverCombatants,
                        handOverTimeline,
                        null,
                        events,
                        handOverCounters.atTurnEnd(state.activeCombatantId()));
            }
        }

        return handTurnOn(
                state,
                resultingRevision,
                handOverCombatants,
                handOverTimeline,
                handOverRandom,
                events,
                events.size(),
                handOverCounters,
                casterLives ? state.activeCombatantId() : null);
    }

    /**
     * Opens the next turn, wherever the one before it stopped.
     *
     * <p>Three handlers arrive here and they arrive differently — a turn that was ended, a card
     * that killed the caster taking it, an authored move that did the same — but what happens
     * next is the same for all three: the turn goes to the first living entry on the timeline,
     * every stunned combatant loses its turn on the way, and the one that finally takes it is
     * given armour, energy, its start-of-turn statuses and a hand. Written once, because the two
     * card handlers had no hand-over at all and simply kept the combatant they came in with,
     * which is how a card that kills its own caster handed the turn to a corpse.
     *
     * <p>An opening that kills its own holder opens the next one rather than settling for it: the
     * turn a combatant is handed can be the turn a Poison finishes it off, and whoever takes over
     * is owed the same ceremony instead of the bare title of active combatant.
     *
     * <p>The counters are the seam between the callers, since each arrives with a different tally
     * already in hand: {@code baseCounters} folded with everything this method adds to
     * {@code events} from {@code foldCountersFrom} on. Ending a turn folds the whole list onto
     * its own state's counters; the card handlers have already accounted for their resolution and
     * fold only what the hand-over adds. Every pass folds that same list afresh from
     * {@code baseCounters}, so however many turns a hand-over has to open, one turn is counted —
     * the one somebody finally gets to play — the way a turn lost to a stun is not counted either.
     *
     * @param foldCountersFrom where in {@code events} this method's own contribution begins
     * @param baseCounters     the tally the caller arrives with
     * @param endedActorId     whose end-of-turn tally to start afresh, or null when no turn was
     *                         ended — a caster killed by its own card had its turn cut short
     *                         rather than closed, and none of its end of turn runs
     */
    private CombatResult handTurnOn(
            CombatState state,
            long resultingRevision,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            List<CombatEvent> events,
            int foldCountersFrom,
            CombatCounters baseCounters,
            String endedActorId
    ) {
        // An opening can kill the very combatant it was opened for: a Poison bites as the turn
        // begins, and the ceremony that precedes it — armour cleared, energy restored, five cards
        // dealt, TurnStarted announced — has already run for somebody who is now a body. When its
        // team survives the blow the combat carries on and somebody else has to act, so the turn
        // is opened again, wholly, for whoever takes it. Handing the state a preferred actor that
        // was already dead let it fall back to the next living entry on its own: legal, silent
        // and false — that combatant became the active one with no turn of its own, no energy, no
        // hand and its armour still up, on the very revision that announced a TurnStarted for the
        // corpse ahead of it.
        //
        // Every pass but the last ends on a death, so a combat cannot spend more passes here than
        // it has combatants; the fight ending is what stops this in practice, and the count is
        // only the proof that it stops at all. It throws rather than settle for the next best
        // state, because a hand-over nobody can account for must not reach the client dressed up
        // as an ordinary turn.
        int openingsLeft = Math.addExact(combatants.size(), 1);
        while (true) {
            if (openingsLeft-- <= 0) {
                throw new IllegalStateException(
                        "turn hand-over opened more turns than the combat has combatants");
            }
            TimelineEntry firstEntry = firstLivingTimelineEntry(timeline, combatants);
            TimelineEntry nextEntry = firstEntry;
            CombatantState nextActor = combatants.stream()
                    .filter(combatant -> combatant.combatantId().equals(firstEntry.combatantId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "timeline references an unknown combatant"));

            // A stunned combatant loses the turn as soon as it starts: it draws nothing and acts
            // not at all, while its statuses still run their course. Skipping happens inside this
            // one revision, because the client expects revisions to arrive one by one.
            //
            // Everybody stunned at once is not itself a problem: a skip is a turn like any other
            // for the statuses that run through it, Stun included, and it counts down the same
            // as everything else. The loop is not bounded by how many combatants there are,
            // because that number has nothing to do with how many skips a Stun needs to clear —
            // a duration of five needs five, on whoever is carrying it, however many others are
            // waiting behind it.
            //
            // What has no number of skips that resolves it is a Stun that never counts down at
            // all: carried at a negative duration, which nothing here ever decrements and
            // nothing here ever expires on its own, so the combatant it never leaves is barred
            // from acting forever. That is caught directly, not guessed at: a single skip that
            // changed nothing at all about the combatant it was spent on will change nothing on
            // any skip after it either, since nothing this loop does depends on anything but
            // that combatant's own statuses. MAX_STUN_SKIPS backs that reasoning up rather than
            // replacing it, for whatever this loop does not know it does not know.
            int skipsAttempted = 0;
            while (isStunned(nextActor)) {
                if (++skipsAttempted > MAX_STUN_SKIPS) {
                    throw new IllegalStateException(
                            "stun-skip loop exceeded " + MAX_STUN_SKIPS + " skips without "
                                    + "either resolving or being caught as unresolvable -- "
                                    + "something this loop does not account for is happening: "
                                    + "combat " + state.combatId());
                }
                List<StatusInstance> statusesBeforeThisSkip = nextActor.statuses();
                StatusEngine.StatusTickResult lostTurnStart = StatusEngine.processTurnStart(
                        withEnergyPilesAndArmor(
                                nextActor,
                                nextActor.baseEnergy(),
                                nextActor.piles(),
                                nextActor.armor()),
                        resultingRevision,
                        state.combatId(),
                        random,
                        relicEngine.shouldClearArmor(nextActor, state, endedActorId));
                CombatantState stunned = lostTurnStart.combatant();
                events.addAll(lostTurnStart.events());
                random = lostTurnStart.random();
                events.add(new TurnStarted(
                        state.combatId(),
                        resultingRevision,
                        stunned.combatantId(),
                        nextEntry.readyAtTick(),
                        stunned.energy()));

                StatusEngine.StatusTickResult lostTurnEnd = StatusEngine.processTurnEnd(
                        stunned, resultingRevision, state.combatId(), random);
                stunned = lostTurnEnd.combatant();
                events.addAll(lostTurnEnd.events());
                random = lostTurnEnd.random();
                stunned = relicEngine.processTurnEnd(stunned, state, events);

                // A combatant killed by its own statuses on the turn it lost to a Stun does not
                // need this loop to give it another one, so a corpse's unmoved statuses are not
                // asked the question below at all.
                if (stunned.hp() > 0
                        && isStunned(stunned)
                        && stunned.statuses().equals(statusesBeforeThisSkip)) {
                    throw new IllegalStateException(
                            "combatant " + stunned.combatantId() + " carries a stun that no "
                                    + "number of skipped turns can resolve: combat "
                                    + state.combatId());
                }

                long calculatedStunnedNextTick = Math.addExact(
                        nextEntry.readyAtTick(),
                        Math.max(0, BattleCalculator.getModifiedValue(
                                stunned.turnDelayTicks(),
                                StatType.TURN_DELAY,
                                new EffectContext(
                                        state, stunned, null, List.of(), null, false, List.of(),
                                        timeline, combatants))));
                long stunnedNextTick = Math.max(
                        Math.incrementExact(nextEntry.readyAtTick()),
                        calculatedStunnedNextTick);
                long stunnedNextSequence = Math.incrementExact(timeline.stream()
                        .mapToLong(TimelineEntry::sequence)
                        .max()
                        .orElseThrow(() -> new IllegalStateException(
                                "active timeline is empty")));
                events.add(new TurnEnded(
                        state.combatId(),
                        resultingRevision,
                        stunned.combatantId(),
                        nextEntry.readyAtTick(),
                        stunnedNextTick));

                combatants = replaceCombatant(combatants, stunned.combatantId(), stunned);
                TimelineEntry lostEntry = nextEntry;
                List<TimelineEntry> rescheduled = new ArrayList<>(timeline.stream()
                        .filter(entry -> !entry.equals(lostEntry))
                        .toList());
                rescheduled.add(new TimelineEntry(
                        stunned.combatantId(), stunnedNextTick, stunnedNextSequence));
                timeline = TimelineOperations.sorted(rescheduled);

                // A turn lost to a stun is still a turn: it opens, its holder's statuses run, and it
                // closes. So it can kill, and the combat is over where it does. Asked only at the two
                // ends of this method, the question skipped over the turns skipped in between: the
                // fight ran on past its own end and opened the turn behind the corpse, whose own
                // statuses could kill again and hand a won combat back as a draw.
                CombatProgress afterLostTurn = outcomeOf(combatants, timeline);
                if (afterLostTurn.finished()) {
                    return transition(
                            state,
                            resultingRevision,
                            random,
                            combatants,
                            timeline,
                            null,
                            events,
                            countersAfter(baseCounters, events, foldCountersFrom, endedActorId));
                }

                nextEntry = firstLivingTimelineEntry(timeline, combatants);
                TimelineEntry entryToStart = nextEntry;
                nextActor = combatants.stream()
                        .filter(combatant -> combatant.combatantId()
                                .equals(entryToStart.combatantId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "timeline references an unknown combatant"));
            }

            // A turn opens the way Unity opens it: armour, energy and every status first, and only
            // then the hand. Dealing first let a status draw, heal or kill against a hand that had
            // already been filled.
            // The armour of the opening turn is decided in one place, inside the status tick, and
            // this passes it the half only the engine knows. Clearing it here as well was clearing
            // it twice, and the status tick's own clearing -- which reads statuses and knows
            // nothing of relics -- overwrote whatever the relics had just decided.
            CombatantState openingActor = withEnergyPilesAndArmor(
                    nextActor, nextActor.baseEnergy(), nextActor.piles(), nextActor.armor());

            StatusEngine.StatusTickResult tickResult = StatusEngine.processTurnStart(
                    openingActor, resultingRevision, state.combatId(), random,
                    relicEngine.shouldClearArmor(nextActor, state, endedActorId));
            CombatantState startedActor = tickResult.combatant();
            int turnStartEventsFrom = events.size();
            events.addAll(tickResult.events());

            List<CombatantState> updatedCombatants = replaceCombatant(combatants, startedActor.combatantId(), startedActor);
            // Une morsure de statut peut tuer en ouvrant le tour : les reliques ont leur mot.
            updatedCombatants = answerAStatusDeath(
                    openingActor, startedActor, updatedCombatants, timeline, tickResult.random(),
                    state.combatId(), resultingRevision, events);
            startedActor = findCombatant(updatedCombatants, startedActor.combatantId());
            RandomState afterSmoke = tickResult.random();
            if (tickResult.expiredBuffs() > 0) {
                Smoked smoked = applySmoke(state.combatId(), resultingRevision, updatedCombatants,
                        startedActor, tickResult.expiredBuffs(), afterSmoke, events);
                updatedCombatants = smoked.combatants();
                afterSmoke = smoked.random();
                startedActor = findCombatant(updatedCombatants, startedActor.combatantId());
            }
            FollowUpResolution jumpLanding = applyJumpLanding(
                    state.combatId(), resultingRevision, updatedCombatants, timeline, afterSmoke,
                    openingActor, startedActor);
            combatants = jumpLanding.combatants();
            timeline = jumpLanding.timeline();
            startedActor = findCombatant(combatants, startedActor.combatantId());
            events.addAll(jumpLanding.events());

            // Five cards are dealt, not topped up to five: a card kept in hand is meant to be a card
            // in hand on top of the new ones.
            PileOperations.DrawResult draw = PileOperations.draw(
                    startedActor.piles(),
                    HAND_SIZE,
                    jumpLanding.random(),
                    despairCardId(startedActor));
            events.addAll(DrawEventFactory.create(
                    state.combatId(),
                    resultingRevision,
                    startedActor.combatantId(),
                    startedActor.piles().hand().size(),
                    draw));
            startedActor = withEnergyPilesAndArmor(
                    startedActor, startedActor.energy(), draw.piles(), startedActor.armor());

            // The turn-start relics are handed the random the deal left, and what they give back
            // is what the revision carries: a relic that draws can reshuffle, and that costs
            // randomness somebody has to account for.
            RelicHandler.TurnStart afterTurnStartRelics =
                    relicEngine.processTurnStart(startedActor, state, draw.random(), events);
            startedActor = afterTurnStartRelics.combatant();
            RandomState randomAfterOpening = afterTurnStartRelics.random();
        
            combatants =
                    replaceCombatant(combatants, startedActor.combatantId(), startedActor);

            // The turn that just opened is this holder's own, mirroring
            // FieldTurnFollowUpStatus.OnTurnStart / Character.StartTurn.
            FollowUpResolution turnStartFollowUps = applyTurnStartFollowUps(
                    state.combatId(), resultingRevision, combatants, timeline, randomAfterOpening,
                    startedActor.combatantId());
            combatants = turnStartFollowUps.combatants();
            timeline = turnStartFollowUps.timeline();
            randomAfterOpening = turnStartFollowUps.random();
            events.addAll(turnStartFollowUps.events());

            events.add(new TurnStarted(
                    state.combatId(),
                    resultingRevision,
                    startedActor.combatantId(),
                    nextEntry.readyAtTick(),
                    startedActor.baseEnergy()));
            PowerPassiveEngine.Result turnStartPowers = powerPassiveEngine.onTurnStarted(
                    state.combatId(), resultingRevision, startedActor.combatantId(), combatants);
            combatants = turnStartPowers.combatants();
            events.addAll(turnStartPowers.events());
            StatusEventReactionResult turnStartReactions = applyStatusEventReactions(
                    state.combatId(), resultingRevision, combatants, events, turnStartEventsFrom);
            combatants = turnStartReactions.combatants();
            events.addAll(turnStartReactions.events());
            timeline = applyCompilationDelay(combatants, timeline, startedActor.combatantId(),
                    events, turnStartEventsFrom);

            // Asked a second time, because the turn that just opened ran its own statuses: a Poison
            // can finish off the combatant the turn was handed to, on the very revision handing it.
            CombatProgress afterTurnStart = outcomeOf(combatants, timeline);
            CombatCounters counters = countersAfter(
                    baseCounters, events, foldCountersFrom, endedActorId).atTurnStart();
            if (afterTurnStart.finished()) {
                return transition(
                        state,
                        resultingRevision,
                        randomAfterOpening,
                        combatants,
                        timeline,
                        null,
                        events,
                        counters);
            }

            // The turn that just opened killed its own holder and left the combat running. Its
            // timeline entry stays where it was, since only a turn that closes is rescheduled,
            // and the next pass steps over the corpse the way the dead are stepped over
            // everywhere else. Nothing of its end of turn runs and none is reported: a turn cut
            // short by a death was never closed, exactly as for a caster killed by its own card.
            if (!isStanding(combatants, startedActor.combatantId())) {
                random = randomAfterOpening;
                timeline = afterTurnStart.timeline();
                continue;
            }

            return transition(
                    state,
                    resultingRevision,
                    randomAfterOpening,
                    combatants,
                    afterTurnStart.timeline(),
                    startedActor.combatantId(),
                    events,
                    counters);
        }
    }

    /**
     * The tally as it stands at this point of the hand-over.
     *
     * <p>A combatant whose turn was cut short by its own death never reaches its end of turn, so
     * there is nothing to start afresh for it and {@code endedActorId} comes in null.
     */
    private static CombatCounters countersAfter(
            CombatCounters baseCounters,
            List<CombatEvent> events,
            int foldFrom,
            String endedActorId
    ) {
        CombatCounters folded =
                baseCounters.observing(events.subList(foldFrom, events.size()));
        return endedActorId == null ? folded : folded.atTurnEnd(endedActorId);
    }

    /**
     * Asks whether the combat is over, and hands back the timeline that goes with the answer.
     *
     * <p>Every handler that lets damage land has to ask before it builds its state. One that never
     * asks — ending a turn never did — builds a state that still calls itself active while a
     * single team is left standing, and the state answers that by throwing, out of the message
     * handler, which leaves the command unanswered and the combat stopped for good. A Burn
     * finishing off the last enemy as its turn closed was enough to do it.
     *
     * <p>Delegates to {@link CombatProgress#of} rather than recomputing the same rule: the engine
     * used to derive this answer on its own here too, and a second copy of the rule is exactly what
     * let the four drift apart and produce six production lockups.
     *
     * @param combatants the combatants as this revision leaves them
     * @param timeline   the timeline as this revision leaves it
     */
    private static CombatProgress outcomeOf(
            List<CombatantState> combatants,
            List<TimelineEntry> timeline
    ) {
        return CombatProgress.of(combatants, timeline);
    }

    /**
     * The state a revision leaves behind — the only place the engine builds one.
     *
     * <p>Three handlers used to build it themselves, each deciding on its own whether the
     * combat was over, who had won, and whose turn it was. Six production lockups came
     * out of that: one handler never asked whether the combat had ended, another ended it
     * without saying so, two handed the turn to a combatant that had just died.
     *
     * <p>This method decides all of it: the outcome, the winner, and the active combatant.
     * {@code nextActiveCombatantId} renders the preferred combatant only when alive — it
     * does not verify that it is first in line on the timeline. Callers must have already
     * verified this themselves, using {@link #holdsTheTurn}, as the two card handlers now do
     * via the handlers' own logic in {@code playCard} and {@code playAiPattern}.
     *
     * @param preferredActiveCombatantId the combatant the caller would keep acting, or
     *                                   null to hand the turn to the next living entry
     */
    private static final org.slf4j.Logger ROSTER_LOGGER =
            org.slf4j.LoggerFactory.getLogger(CombatEngine.class);

    /**
     * Personne ne quitte un combat parce qu'un pas l'a oublié.
     *
     * <p>Un pas qui ne rend que les combattants qu'il a touchés, pris pour la table entière, fait
     * disparaître les autres : {@link CombatProgress} ne voit plus qu'une équipe debout, termine
     * le combat, et retire de la file ceux qui manquent. Le client, lui, ne les voit pas mourir --
     * leurs PV n'ont jamais bougé -- et le combat s'arrête sans une animation (Électrosaure et
     * Drone-égide, une fois le Milieu continu posé). Une révision ne fait jamais sortir personne
     * de la table : les manquants sont gardés tels qu'ils étaient, avec leur place dans la file,
     * et l'oubli est journalisé pour qu'on retrouve le pas fautif.
     */
    private static List<CombatantState> withNobodyLost(
            CombatState previous,
            long revision,
            List<CombatantState> combatants,
            List<CombatEvent> events
    ) {
        Set<String> present = combatants.stream()
                .map(CombatantState::combatantId)
                .collect(java.util.stream.Collectors.toSet());
        List<String> lost = previous.combatants().stream()
                .map(CombatantState::combatantId)
                .filter(id -> !present.contains(id))
                .toList();
        if (lost.isEmpty()) {
            return combatants;
        }
        ROSTER_LOGGER.error(
                "STS combat {} revision {} dropped {} from its roster; kept as they were. Events: {}",
                previous.combatId(), revision, lost, events);
        java.util.Map<String, CombatantState> byId = new java.util.LinkedHashMap<>();
        combatants.forEach(combatant -> byId.put(combatant.combatantId(), combatant));
        List<CombatantState> kept = new ArrayList<>();
        for (CombatantState before : previous.combatants()) {
            CombatantState now = byId.remove(before.combatantId());
            kept.add(now != null ? now : before);
        }
        kept.addAll(byId.values());
        return kept;
    }

    /** Gives back its place in the file to every living combatant a step left without one. */
    private static List<TimelineEntry> withNobodysTurnLost(
            CombatState previous,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline
    ) {
        Set<String> scheduled = timeline.stream()
                .map(TimelineEntry::combatantId)
                .collect(java.util.stream.Collectors.toSet());
        List<TimelineEntry> restored = new ArrayList<>(timeline);
        for (CombatantState combatant : combatants) {
            if (combatant.hp() <= 0 || scheduled.contains(combatant.combatantId())) {
                continue;
            }
            previous.timeline().stream()
                    .filter(entry -> entry.combatantId().equals(combatant.combatantId()))
                    .findFirst()
                    .ifPresent(restored::add);
        }
        return restored.size() == timeline.size()
                ? timeline
                : TimelineOperations.sorted(restored);
    }

    static CombatResult transition(
            CombatState previous,
            long revision,
            RandomState random,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            String preferredActiveCombatantId,
            List<CombatEvent> events,
            CombatCounters counters
    ) {
        combatants = withNobodyLost(previous, revision, combatants, events);
        timeline = withNobodysTurnLost(previous, combatants, timeline);
        // Ce qui a baissé des PV hors d'un effet de carte -- un Poison, un Milieu continu, une
        // Brûlure -- peut aussi faire mûrir un Sadisme : la question est posée une dernière fois
        // ici, sur la table que la révision va porter.
        combatants = fr.insastronaute.webapi.sts.combat.engine.status.StatusEvolution.evolve(
                previous.combatId(), revision, combatants, events);
        CombatProgress progress = CombatProgress.of(combatants, timeline);
        if (progress.finished()) {
            events.add(new CombatEnded(
                    previous.combatId(), revision, progress.winnerTeamId()));
            return CombatResult.accepted(
                    new CombatState(
                            previous.combatId(),
                            revision,
                            previous.dataVersion(),
                            CombatStatus.FINISHED,
                            progress.winnerTeamId(),
                            random,
                            combatants,
                            progress.timeline(),
                            null,
                            counters),
                    events);
        }
        return CombatResult.accepted(
                new CombatState(
                        previous.combatId(),
                        revision,
                        previous.dataVersion(),
                        CombatStatus.ACTIVE,
                        null,
                        random,
                        combatants,
                        progress.timeline(),
                        progress.nextActiveCombatantId(preferredActiveCombatantId, combatants),
                        counters),
                events);
    }

    /**
     * Whose turn it is now: the first entry on the timeline that still belongs to somebody alive.
     *
     * <p>The state already says this and says it in these words — the active combatant must be the
     * first <em>living</em> timeline entry — and it tolerates the dead anywhere on the list, since
     * it only ever asks that the living be there. The engine read the first entry instead, which is
     * not the same thing the moment a combatant is killed by its own statuses: it keeps the place
     * it was just given back, ahead of everybody, and the turn went to that corpse. The state
     * answered by refusing to be built, out of the message handler, which left the command
     * unanswered and the combat stuck on its revision for good — the way a player was stuck in
     * production. The rule was written down in one place and ignored in the other; it is honoured
     * here rather than restated.
     *
     * <p>Nobody left standing on the timeline is not the way a combat ends. Both callers have just
     * asked {@link #outcomeOf} and would have returned through {@link #transition} with the combat
     * marked finished if it were over, so two teams are still standing here and the state requires
     * every living combatant to hold an entry. An empty answer would mean the timeline had lost one,
     * which is a broken state rather than a finished combat, and it is reported as such.
     *
     * @param timeline   the timeline as this revision leaves it, corpses and all
     * @param combatants the combatants as this revision leaves them
     */
    private static TimelineEntry firstLivingTimelineEntry(
            List<TimelineEntry> timeline,
            List<CombatantState> combatants
    ) {
        Set<String> living = combatants.stream()
                .filter(combatant -> combatant.hp() > 0)
                .map(CombatantState::combatantId)
                .collect(java.util.stream.Collectors.toSet());
        return timeline.stream()
                .filter(entry -> living.contains(entry.combatantId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "active combat requires a living timeline entry"));
    }

    /**
     * Whether {@code combatantId} is still on its feet once this revision's damage has landed.
     *
     * <p>Asked by the handlers that let a combatant act, about the combatant that acted: a card
     * or an authored move can take its own caster down, and the state it leaves behind has to be
     * built around somebody who can still hold a turn.
     */
    private static boolean isStanding(List<CombatantState> combatants, String combatantId) {
        return combatants.stream()
                .anyMatch(combatant -> combatant.combatantId().equals(combatantId)
                        && combatant.hp() > 0);
    }

    /**
     * Whether this combatant still holds the turn.
     *
     * <p>Two things must be true, because {@link CombatState} asks for both: it has to be
     * alive, and it has to be the first living entry on the timeline. Asking only the
     * first is what froze a combat when a card delayed its own caster past somebody
     * else — the caster stayed active while no longer first in line, and the state
     * refused to be built, out of the message handler.</p>
     */
    private static boolean holdsTheTurn(
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            String combatantId
    ) {
        if (!isStanding(combatants, combatantId)) {
            return false;
        }
        Set<String> living = new HashSet<>();
        for (CombatantState combatant : combatants) {
            if (combatant.hp() > 0) {
                living.add(combatant.combatantId());
            }
        }
        return timeline.stream()
                .map(TimelineEntry::combatantId)
                .filter(living::contains)
                .findFirst()
                .map(combatantId::equals)
                .orElse(false);
    }

    /**
     * Clears the hand at the end of a turn.
     *
     * <p>A card marked to be kept stays in hand instead of being discarded, and every card loses
     * the bonuses that were only meant to last this turn, whether it is kept or discarded.
     */
    /** Ce qu'une carte jouée d'elle-même laisse derrière elle. */
    private record MechanicalPlays(
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random
    ) {
    }

    /**
     * Joue, à la fermeture du tour, les cartes que leur porteur n'a pas à jouer : celles que
     * l'enchantement Mécanique marque (« S'active automatiquement à la fin du tour ») et celles
     * qui portent le marqueur Automatique, comme le fait {@code DeckManager.DiscardHand} dans
     * Unity.
     *
     * <p>Rien n'en tenait compte ici : le handler de Mécanique est vide, et l'enchantement ne
     * faisait donc rien du tout. Elles se jouent sans payer d'énergie -- Unity les joue en
     * {@code ignoreEnergy} -- et avant la défausse, qui ramasse ensuite ce qu'elles laissent.
     *
     * <p>La main est parcourue telle qu'elle était en ouvrant la fermeture du tour : une carte
     * qu'une autre vient d'ajouter à la main attendra le tour suivant, faute de quoi une carte
     * qui s'en donne une autre les jouerait toutes à la file.
     */
    private MechanicalPlays playMechanicalCards(
            CombatState state,
            long revision,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            String actorId,
            RandomState random,
            List<CombatEvent> events
    ) {
        CombatantState closing = findCombatant(combatants, actorId);
        if (closing == null || closing.hp() <= 0) {
            return new MechanicalPlays(combatants, timeline, random);
        }
        List<CombatantState> current = combatants;
        List<TimelineEntry> currentTimeline = timeline;
        RandomState currentRandom = random;
        for (CardInstance held : List.copyOf(closing.piles().hand())) {
            CombatantState holder = findCombatant(current, actorId);
            if (holder == null || holder.hp() <= 0) {
                break;
            }
            CardInstance card = holder.piles().hand().stream()
                    .filter(candidate -> candidate.instanceId().equals(held.instanceId()))
                    .findFirst()
                    .orElse(null);
            CardDefinition authored = card == null ? null : catalog.find(card.definitionId());
            if (authored == null) {
                continue;
            }
            CardDefinition definition = EffectiveCard.of(card, authored);
            if (!playsItself(card, definition)) {
                continue;
            }
            TargetResolution targets = autoFollowUpTargets(
                    current, holder, definition.targetingMode(), null, currentRandom);
            currentRandom = targets.random();
            if (targets.targetIds().isEmpty()
                    && definition.targetingMode() != TargetingMode.NONE) {
                continue;
            }

            current = replaceCombatant(current, actorId, withEnergyPilesAndArmor(
                    holder, holder.energy(), removeFromHand(holder.piles(), card), holder.armor()));
            EffectResolver.Resolution resolution = effectResolver.resolve(
                    state.combatId(), revision, current, currentTimeline, currentRandom, actorId,
                    targets.targetIds(), definition, card.enchantments());
            current = resolution.combatants();
            currentTimeline = resolution.timeline();
            currentRandom = resolution.random();
            events.add(new CardPlayed(state.combatId(), revision, actorId, card.instanceId(),
                    card.definitionId(), targets.targetIds()));
            events.addAll(resolution.events());

            CombatantState afterPlay = findCombatant(current, actorId);
            if (afterPlay != null) {
                current = replaceCombatant(current, actorId, withEnergyPilesAndArmor(
                        afterPlay, afterPlay.energy(),
                        afterPlaying(afterPlay.piles(), card, definition), afterPlay.armor()));
            }
        }
        return new MechanicalPlays(current, currentTimeline, currentRandom);
    }

    /** Whether this copy plays itself as the turn closes rather than being discarded. */
    private static boolean playsItself(CardInstance card, CardDefinition definition) {
        return EnchantmentCatalog.levelOf(card, EnchantmentCatalog.MECHANICAL) > 0
                || hasTag(card, definition,
                        fr.insastronaute.webapi.sts.combat.catalog.CardTag.AUTOMATIC);
    }

    private CardPiles discardHand(
            CombatState state,
            long revision,
            CombatantState actor,
            List<CombatEvent> events
    ) {
        List<CardInstance> discard = new ArrayList<>(actor.piles().discard());
        List<CardInstance> exhaust = new ArrayList<>(actor.piles().exhaust());
        List<CardInstance> retained = new ArrayList<>();
        for (CardInstance card : actor.piles().hand()) {
            CardInstance lasting = card.withoutTemporaryModifiers();
            if (isRetained(lasting)) {
                retained.add(lasting);
                continue;
            }
            boolean evaporates = isEthereal(lasting);
            List<CardInstance> destination = evaporates ? exhaust : discard;
            destination.add(lasting);
            events.add(new CardMoved(
                    state.combatId(),
                    revision,
                    actor.combatantId(),
                    lasting.instanceId(),
                    lasting.definitionId(),
                    HAND_PILE,
                    evaporates ? EXHAUST_PILE : DISCARD_PILE,
                    destination.size() - 1));
        }
        return new CardPiles(
                actor.piles().draw(),
                retained,
                discard,
                exhaust).withoutTemporaryModifiers();
    }

    private boolean isRetained(CardInstance card) {
        return hasTag(card, catalog.find(card.definitionId()),
                fr.insastronaute.webapi.sts.combat.catalog.CardTag.RETAIN);
    }

    /**
     * Whether this card evaporates at the end of the turn instead of being discarded.
     *
     * <p>An ethereal card is one its holder has this turn and no other: it never reaches the
     * discard pile, so it cannot be drawn a second time. Nothing authored carries the tag today
     * -- it arrives on the copies ITI tears off the other side, which are meant to be spent at
     * once or lost.
     */
    private boolean isEthereal(CardInstance card) {
        return hasTag(card, catalog.find(card.definitionId()),
                fr.insastronaute.webapi.sts.combat.catalog.CardTag.ETHEREAL);
    }

    private static TimelineEntry requireUniqueActiveTimelineEntry(CombatState state) {
        Set<String> combatantIds = state.combatants().stream()
                .map(CombatantState::combatantId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> timelineIds = new HashSet<>();
        for (TimelineEntry entry : state.timeline()) {
            if (!combatantIds.contains(entry.combatantId())) {
                throw new IllegalStateException("timeline references an unknown combatant");
            }
            if (!timelineIds.add(entry.combatantId())) {
                throw new IllegalStateException("timeline contains duplicate combatant entries");
            }
        }
        List<TimelineEntry> activeEntries = state.timeline().stream()
                .filter(entry -> entry.combatantId().equals(state.activeCombatantId()))
                .toList();
        if (activeEntries.size() != 1) {
            throw new IllegalStateException(
                    "active combatant must have exactly one timeline entry");
        }
        return activeEntries.getFirst();
    }

    /**
     * Whether a combatant is stunned, and so loses the turn it is about to take.
     */
    private static boolean isStunned(CombatantState combatant) {
        return combatant.statuses().stream()
                .anyMatch(status -> "Stun".equals(status.statusType())
                        && (status.duration() != 0 || status.value() > 0));
    }

    private static List<TimelineEntry> advanceTimeline(
            CombatState state,
            TimelineEntry activeEntry,
            long nextReadyAtTick,
            long nextSequence
    ) {
        return advanceTimeline(state, state.timeline(), activeEntry, nextReadyAtTick, nextSequence);
    }

    /**
     * @param timeline la file telle que la fin du tour la laisse, qui n'est plus celle de l'état
     *                 quand une carte jouée d'elle-même y a touché
     */
    private static List<TimelineEntry> advanceTimeline(
            CombatState state,
            List<TimelineEntry> timeline,
            TimelineEntry activeEntry,
            long nextReadyAtTick,
            long nextSequence
    ) {
        List<TimelineEntry> advanced = timeline.stream()
                // Par combattant plutôt que par entrée : celle du sortant a pu être réécrite
                // entre-temps, et la laisser en place lui en aurait donné deux.
                .filter(entry -> !entry.combatantId().equals(activeEntry.combatantId()))
                .filter(entry -> {
                    CombatantState combatant = findCombatant(state, entry.combatantId());
                    return combatant != null && combatant.hp() > 0;
                })
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        advanced.add(new TimelineEntry(
                activeEntry.combatantId(),
                nextReadyAtTick,
                nextSequence));
        return TimelineOperations.sorted(advanced);
    }

    private CombatResult validatePlayCard(
            CombatState state,
            CombatantState actor,
            PlayCardCommand command
    ) {
        List<CardInstance> matches = actor.piles().hand().stream()
                .filter(card -> card.instanceId().equals(command.cardInstanceId()))
                .toList();
        if (matches.size() != 1) {
            return reject(
                    state,
                    RejectionCode.CARD_NOT_IN_HAND,
                    "Card instance is not uniquely present in the actor hand");
        }

        CardDefinition definition;
        try {
            definition = EffectiveCard.of(
                    matches.getFirst(), catalog.require(matches.getFirst().definitionId()));
        } catch (IllegalArgumentException exception) {
            return reject(
                    state,
                    RejectionCode.UNSUPPORTED_EFFECT,
                    "Card definition is unavailable or unsupported");
        }

        // An X-cost card is always affordable: it costs whatever the caster happens to hold.
        int cost = costOf(state, actor, matches.getFirst(), definition);
        if (actor.energy() < cost) {
            return reject(
                    state,
                    RejectionCode.INSUFFICIENT_ENERGY,
                    "Actor energy is below card cost " + cost);
        }
        if (!hasValidTargets(state, actor, definition.targetingMode(), command.targetIds())) {
            return reject(
                    state,
                    RejectionCode.INVALID_TARGET,
                    "Targets do not satisfy card targeting mode " + definition.targetingMode());
        }
        List<EffectDefinition> selectionEffects = definition.effects().stream()
                .filter(effect -> effect.type() == fr.insastronaute.webapi.sts.combat.catalog.EffectType.CARD_SELECTION)
                .toList();
        if (selectionEffects.isEmpty()) {
            if (!command.selectedCardInstanceIds().isEmpty()) {
                return reject(state, RejectionCode.INVALID_CARD_SELECTION,
                        "Card selections were supplied for a card without CardSelection");
            }
            return null;
        }
        if (selectionEffects.size() != 1) {
            return reject(state, RejectionCode.UNSUPPORTED_EFFECT,
                    "Multiple CardSelection effects on one card are not supported yet");
        }

        EffectDefinition selection = selectionEffects.getFirst();
        java.util.Optional<String> unsupported =
                fr.insastronaute.webapi.sts.combat.catalog.CardSelectionSupport
                        .unsupportedReason(selection);
        if (unsupported.isPresent()) {
            return reject(state, RejectionCode.UNSUPPORTED_EFFECT, unsupported.get());
        }

        List<CardSelectionCandidate> candidates = new CardSelectionResolver(catalog)
                .candidates(selection, actor.piles(), command.cardInstanceId());
        List<String> selected = command.selectedCardInstanceIds();
        int requiredCount = CardSelectionResolver.requiredCount(selection, candidates.size());
        // Une sélection qui prend toutes les cartes éligibles ne laisse aucun choix au joueur :
        // le client peut ne rien envoyer et laisser le serveur les prendre toutes (playCard).
        // Exiger qu'il les nomme lui-même, c'était exiger qu'il refasse à l'identique un
        // filtrage qu'il n'a pas toujours su faire -- Inépuisable filtre sur Épuisement, que le
        // client ne connaissait pas, et partait donc en refus avec une liste vide.
        if (selected.isEmpty() && requiredCount == candidates.size()) {
            return null;
        }
        if (selected.size() != requiredCount) {
            return reject(state, RejectionCode.INVALID_CARD_SELECTION,
                    "CardSelection expects " + requiredCount + " card(s) among ["
                            + CardSelectionResolver.describe(candidates) + "]");
        }
        if (selected.size() != new java.util.HashSet<>(selected).size()) {
            return reject(state, RejectionCode.INVALID_CARD_SELECTION,
                    "The same card instance was selected twice");
        }
        Set<String> selectableIds = candidates.stream()
                .map(CardSelectionCandidate::instanceId)
                .collect(java.util.stream.Collectors.toSet());
        if (!selectableIds.containsAll(selected)) {
            return reject(state, RejectionCode.INVALID_CARD_SELECTION,
                    "Selected card is not eligible, expected one of ["
                            + CardSelectionResolver.describe(candidates) + "]");
        }
        return null;
    }

    /**
     * The cards a selection actually takes.
     *
     * <p>What the player named, or -- when the player named nothing because there was nothing to
     * choose -- every eligible card, in the order validation listed them. Spelled out here rather
     * than left to the selection handler, because the card reads how many it took: consuming
     * three Atoms for armour pays out for three.
     */
    private List<String> effectiveSelection(
            CombatantState actor, CardDefinition definition, PlayCardCommand command) {
        if (!command.selectedCardInstanceIds().isEmpty()) {
            return command.selectedCardInstanceIds();
        }
        EffectDefinition selection = definition.effects().stream()
                .filter(effect -> effect.type()
                        == fr.insastronaute.webapi.sts.combat.catalog.EffectType.CARD_SELECTION)
                .findFirst()
                .orElse(null);
        if (selection == null) {
            return List.of();
        }
        List<CardSelectionCandidate> candidates = new CardSelectionResolver(catalog)
                .candidates(selection, actor.piles(), command.cardInstanceId());
        if (CardSelectionResolver.requiredCount(selection, candidates.size()) != candidates.size()) {
            return List.of();
        }
        return candidates.stream().map(CardSelectionCandidate::instanceId).toList();
    }

    private CombatResult playCard(
            CombatState state,
            CombatantState actor,
            PlayCardCommand command
    ) {
        CardInstance card = actor.piles().hand().stream()
                .filter(candidate -> candidate.instanceId().equals(command.cardInstanceId()))
                .findFirst()
                .orElseThrow();
        CardDefinition definition = EffectiveCard.of(card, catalog.require(card.definitionId()));
        long resultingRevision = Math.incrementExact(state.revision());
        int spentEnergy = costOf(state, actor, card, definition);
        int remainingEnergy = Math.subtractExact(actor.energy(), spentEnergy);
        CombatantState paidActor = withEnergyPilesAndArmor(
                spendCostNullifyCharge(actor, definition),
                remainingEnergy,
                removeFromHand(actor.piles(), card),
                actor.armor());
        List<CombatantState> paidCombatants =
                replaceCombatant(state.combatants(), actor.combatantId(), paidActor);
        TargetResolution targets = resolveTargets(state, actor, definition.targetingMode(), command.targetIds());
        ProvocationSpent provocation = spendProvocation(
                state.combatId(), resultingRevision, paidCombatants, targets.provocationBearerId());
        paidCombatants = provocation.combatants();

        // Unity dépense l'énergie avant de résoudre le moindre effet (CombatManager.cs:3544 ->
        // Character.SpendEnergy) : le prix de la carte compte déjà dans « énergie dépensée ce
        // tour » quand ses conditions et ses modificateurs sont lus. Ne le plier qu'après la
        // résolution le cachait à la carte qui l'avait payé.
        EnergySpent energySpent = new EnergySpent(
                state.combatId(), resultingRevision, actor.combatantId(),
                spentEnergy, remainingEnergy);
        CombatCounters countersWithSpend = state.counters().observing(List.of(energySpent));
        PowerPassiveEngine.Result paymentPowers = powerPassiveEngine.onEvents(
                state.combatId(), resultingRevision, List.of(energySpent), paidCombatants);

        EffectResolver.Resolution resolution = effectResolver.resolve(
                state.combatId(),
                resultingRevision,
                paymentPowers.combatants(),
                state.timeline(),
                targets.random(),
                actor.combatantId(),
                targets.targetIds(),
                definition,
                card.enchantments(),
                effectiveSelection(actor, definition, command),
                countersWithSpend,
                card.instanceId(),
                actor.energy());
        CombatantState resolvedActor = resolution.combatants().stream()
                .filter(combatant -> combatant.combatantId().equals(actor.combatantId()))
                .findFirst()
                .orElseThrow();
        List<CombatEvent> vigorEvents = new ArrayList<>();
        CombatantState spentActor = spendCritical(
                state.combatId(), resultingRevision,
                spendVigor(
                        state.combatId(), resultingRevision, resolvedActor, definition,
                        resolution.events(), paidActor.statuses(), vigorEvents),
                definition, resolution.events(), vigorEvents);
        CombatantState discardedActor = withEnergyPilesAndArmor(
                spentActor,
                spentActor.energy(),
                afterPlaying(spentActor.piles(), card, definition),
                spentActor.armor());
        List<CombatantState> resultingCombatants = replaceCombatant(
                resolution.combatants(), actor.combatantId(), discardedActor);

        // Once the card has landed, each target is answered in the order Unity asks: the
        // caster's Sadism III, then the target's own Crystallize.
        FollowUpResolution answered = answerTheTargets(
                state.combatId(), resultingRevision, resultingCombatants, resolution.timeline(),
                resolution.random(), actor.combatantId(), targets.targetIds(), definition);
        resultingCombatants = answered.combatants();

        // Les statuts répondent à la carte entière avant les reliques : Domination doit voir ce
        // que la carte a fait de chacune de ses cibles, et le savoir avant qu'une relique ne s'en
        // mêle. Ce qu'ils demandent — un coup de plus, un tour effacé — est exécuté juste après.
        List<CombatEvent> statusReactionEvents = new ArrayList<>();
        fr.insastronaute.webapi.sts.combat.engine.status.CardPlayReactions.Outcome cardReactions =
                fr.insastronaute.webapi.sts.combat.engine.status.CardPlayReactions.toCardPlayed(
                        state.combatId(), resultingRevision, resultingCombatants,
                        actor.combatantId(), definition, targets.targetIds(),
                        resolution.events(), statusReactionEvents);
        resultingCombatants = cardReactions.combatants();
        List<TimelineEntry> timelineAfterReactions =
                deleteNextTurns(answered.timeline(), resultingCombatants,
                        cardReactions.turnsToDelete());
        EffectResolver.Resolution overpower = replayForOverpower(
                state, resultingRevision, resultingCombatants, timelineAfterReactions,
                answered.random(), actor.combatantId(), definition, card,
                resolution.counters(), cardReactions.repeatTargetIds(), statusReactionEvents);
        resultingCombatants = overpower.combatants();
        timelineAfterReactions = overpower.timeline();

        // Relics answer the card once its effects have resolved and it has reached its pile,
        // which is where Unity calls them from (CombatManager.cs:1613-1618). The counters they
        // read still stand as they did before this card, as Unity's relics do.
        List<CombatEvent> relicEvents = new ArrayList<>(statusReactionEvents);
        fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep afterRelics =
                relicEngine.processCardPlayed(
                        discardedActor,
                        definition,
                        state.counters(),
                        resultingCombatants,
                        timelineAfterReactions,
                        overpower.random(),
                        state.combatId(),
                        resultingRevision,
                        relicEvents);

        // And then the other side's relics, which watch what is thrown at them rather than what
        // they throw: ITI tears a copy off every card played against its bearer. Asked after the
        // caster's own, so a relic that answers a card cannot be answering one that a relic of
        // the caster's has not finished with.
        fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep afterOpponentRelics =
                relicEngine.processOpponentCardPlayed(
                        discardedActor,
                        definition,
                        state.counters(),
                        afterRelics.combatants(),
                        afterRelics.timeline(),
                        afterRelics.random(),
                        state.combatId(),
                        resultingRevision,
                        new ArrayList<>(afterRelics.events()));
        afterRelics = afterOpponentRelics;
        List<CombatantState> combatantsAfterRelics = afterRelics.combatants();

        // Cards this one carries alongside its own effects: CardFollowUp on the caster and
        // AnyCardFollowUp on everyone else, both watching what was just played.
        FollowUpResolution followUps = applyCardFollowUps(
                state.combatId(),
                resultingRevision,
                combatantsAfterRelics,
                afterRelics.timeline(),
                afterRelics.random(),
                actor.combatantId(),
                targets.targetIds().isEmpty() ? null : targets.targetIds().getFirst(),
                definition.type(),
                false);
        List<CombatEvent> imitationEvents = new ArrayList<>();
        List<CombatantState> combatantsAfterFollowUps = imitate(
                state.combatId(), resultingRevision, followUps.combatants(),
                targets.targetIds(), card.definitionId(), imitationEvents);

        // The engine's own events are counted apart, because the resolution has already been
        // accounted for and counting a list twice would double every tally it feeds.
        CardPlayed cardPlayed = new CardPlayed(
                state.combatId(),
                resultingRevision,
                actor.combatantId(),
                card.instanceId(),
                card.definitionId(),
                targets.targetIds());
        List<CombatEvent> playEvents = List.of(cardPlayed, energySpent);
        List<CombatEvent> events = new ArrayList<>(playEvents);
        events.addAll(provocation.events());
        events.addAll(paymentPowers.events());
        events.addAll(resolution.events());
        events.addAll(vigorEvents);
        events.addAll(answered.events());
        events.addAll(afterRelics.events());
        events.addAll(followUps.events());
        events.addAll(imitationEvents);
        PowerPassiveEngine.Result cardPowers = powerPassiveEngine.onCardPlayed(
                state.combatId(), resultingRevision, cardPlayed, definition, card, combatantsAfterFollowUps);
        List<CombatantState> combatantsAfterPowers = cardPowers.combatants();
        events.addAll(cardPowers.events());
        List<CombatEvent> sapEvents = new ArrayList<>();
        combatantsAfterPowers = biteWithSap(
                state.combatId(), resultingRevision, combatantsAfterPowers, sapEvents);
        events.addAll(sapEvents);
        if (burns(card, definition)) {
            int exhaustedAt = exhaustIndexOf(combatantsAfterPowers, actor.combatantId(), card);
            if (exhaustedAt >= 0) {
                CardMoved exhausted = new CardMoved(state.combatId(), resultingRevision,
                        actor.combatantId(), card.instanceId(), card.definitionId(), HAND_PILE,
                        EXHAUST_PILE, exhaustedAt, card);
                PowerPassiveEngine.Result exhaustPowers = powerPassiveEngine.onEvents(
                        state.combatId(), resultingRevision, List.of(exhausted), combatantsAfterPowers);
                combatantsAfterPowers = exhaustPowers.combatants();
                events.add(exhausted);
                events.addAll(exhaustPowers.events());
            }
        }

        StatusEventReactionResult statusEventReactions = applyStatusEventReactions(
                state.combatId(), resultingRevision, combatantsAfterPowers, events, 0);
        combatantsAfterPowers = statusEventReactions.combatants();
        events.addAll(statusEventReactions.events());
        List<TimelineEntry> timelineAfterStatusEvents = applyCompilationDelay(
                combatantsAfterPowers, followUps.timeline(), state.activeCombatantId(), events, 0);
        // La Distorsion temporelle du lanceur, s'il en porte une : elle tire sur le flux du
        // combat, donc ce qui suit doit repartir du flux qu'elle laisse et non de celui d'avant.
        Distorted distorted = applyTimeDistortion(
                combatantsAfterPowers, timelineAfterStatusEvents, followUps.random(),
                actor.combatantId(), definition);
        timelineAfterStatusEvents = distorted.timeline();

        CombatProgress outcome = outcomeOf(combatantsAfterPowers, timelineAfterStatusEvents);
        // La dépense est déjà comptée dans les compteurs de la résolution : la replier ici la
        // compterait deux fois. Les événements des suiveurs (cristallisation, reliques, imitations)
        // ne l'ont jamais été, et chaque événement émis doit l'être une fois.
        CombatCounters counters = resolution.counters()
                .observing(paymentPowers.events())
                .observing(answered.events())
                .observing(afterRelics.events())
                .observing(followUps.events())
                .observing(imitationEvents)
                .observing(sapEvents)
                .afterPlaying(card.instanceId(), card.definitionId(), definition.type())
                .afterAction();
        if (outcome.finished()) {
            return transition(
                    state,
                    resultingRevision,
                    distorted.random(),
                        combatantsAfterPowers,
                                        timelineAfterStatusEvents,
                    state.activeCombatantId(),
                    events,
                    counters);
        }

        // A card can kill the caster taking the turn, or delay it past somebody else still
        // standing: ultravibration damages whoever plays it, and nine shipped cards carry a
        // DelayTurn aimed at their own caster. Either way the caster this handler came in with
        // can no longer hold the turn — dead, or no longer first in line — and reusing it built
        // an active combat CombatState refuses, out of the message handler, leaving the command
        // unanswered and the player's combat stuck on that revision for good. The turn stops
        // where the caster lost it and the next living entry takes it.
        if (!holdsTheTurn(combatantsAfterPowers, outcome.timeline(), state.activeCombatantId())) {
            return handOverTheLostTurn(
                    state,
                    resultingRevision,
                    combatantsAfterPowers,
                    outcome.timeline(),
                    distorted.random(),
                    counters,
                    events);
        }

        CombatResult played = transition(
                state,
                resultingRevision,
                distorted.random(),
                combatantsAfterPowers,
                timelineAfterStatusEvents,
                state.activeCombatantId(),
                events,
                counters);

        // Une carte qui porte un effet EndTurn ferme le tour derrière elle. La carte est d'abord
        // jouée jusqu'au bout — elle est en défausse, ses suivis ont couru, ses reliques ont
        // répondu — puis le tour se termine comme si le joueur l'avait fini lui-même. Le faire
        // depuis le handler était impossible, et se contenter d'y vider l'énergie ne terminait
        // rien du tout.
        if (resolution.endsTurn() && played.accepted()
                && played.state().status() == CombatStatus.ACTIVE
                && actor.combatantId().equals(played.state().activeCombatantId())) {
            return endTurnAfterCard(played, actor.combatantId());
        }
        return played;
    }

    /**
     * Ferme le tour qu'une carte vient de demander de finir.
     *
     * <p>La fin de tour est menée par le même chemin que la commande du joueur, pour qu'un tour
     * fini par une carte soit un tour fini : mêmes statuts de fin, même défausse, même passage de
     * main. Les événements de la carte et ceux de la fin de tour voyagent ensemble, dans cet
     * ordre, sur la révision de la fin de tour — le client rejoue la carte puis la voit se
     * refermer.
     *
     * <p>Un refus est impossible ici — le tour vient d'être vérifié comme celui du lanceur — mais
     * s'il arrivait, la carte reste jouée : mieux vaut un tour qui ne se ferme pas qu'une carte
     * qui n'a jamais eu lieu.
     */
    private CombatResult endTurnAfterCard(CombatResult played, String actorId) {
        CombatState state = played.state();
        CombatantState actor = findCombatant(state, actorId);
        if (actor == null) {
            return played;
        }
        CombatResult ended = endTurn(state, actor);
        if (!ended.accepted()) {
            return played;
        }
        List<CombatEvent> events = new ArrayList<>(played.events());
        events.addAll(ended.events());
        return CombatResult.accepted(ended.state(), events);
    }

    private CombatResult playAiPattern(
            CombatState state,
            CombatantState actor,
            fr.insastronaute.webapi.sts.combat.command.AiPlayPatternCommand command
    ) {
        CardDefinition definition = catalog.require(command.cardId());
        long resultingRevision = Math.incrementExact(state.revision());
        // A move is priced like any card: a "Coût nul" its caster carries makes it free, and
        // spends its charge here exactly as a played card spends it (CostNullifyStatus.cs).
        CardInstance move = new CardInstance(
                "pattern-" + command.cardId() + "-" + resultingRevision, command.cardId());
        int spentEnergy = Math.min(actor.energy(), costOf(state, actor, move, definition));
        int remainingEnergy = Math.subtractExact(actor.energy(), spentEnergy);

        CombatantState paidActor = withEnergyPilesAndArmor(
                spendCostNullifyCharge(actor, definition),
                remainingEnergy,
                actor.piles(),
                actor.armor());
        // A move somebody forced on this enemy is spent instead of the pattern, which stays where
        // it was: the enemy resumes its own moves once the forced ones run out.
        StatusInstance forced = forcedMove(actor, command.cardId());
        boolean patternWasChosen = command.nextPatternIndex() >= 0 && forced == null;
        if (forced == null) {
            // Where the pattern goes next is the policy's to say, because it holds the graph the
            // enemy was authored with. Stepping forward by one is only the fallback for a caller
            // that has nothing to say.
            paidActor = paidActor.withPatternIndex(command.nextPatternIndex() >= 0
                    ? command.nextPatternIndex()
                    : actor.patternIndex() + 1);
        } else {
            paidActor = withStatuses(paidActor, spent(actor.statuses(), forced));
        }
        
        List<CombatantState> paidCombatants =
                replaceCombatant(state.combatants(), actor.combatantId(), paidActor);
        TargetResolution targets = resolveTargets(state, actor, definition.targetingMode(), command.targetIds());
        ProvocationSpent provocation = spendProvocation(
                state.combatId(), resultingRevision, paidCombatants, targets.provocationBearerId());
        paidCombatants = provocation.combatants();
        // Choosing that move cost the stream a step. Leaving the stream where it was would have
        // the graph answer the same thing every time it was asked.
        RandomState afterTransition = patternWasChosen
                ? targets.random().advance()
                : targets.random();

        // Un coup de pattern se paie avant de se résoudre, comme une carte : son propre prix
        // compte déjà dans « énergie dépensée ce tour » quand ses conditions sont lues. Et il
        // lit le compteur du combat, pas un compteur frais -- partir de rien cachait ses
        // conditions et effaçait du combat tout ce que le tour avait accumulé.
        fr.insastronaute.webapi.sts.combat.event.EnergySpent energySpent =
                new fr.insastronaute.webapi.sts.combat.event.EnergySpent(
                        state.combatId(), resultingRevision, actor.combatantId(),
                        spentEnergy, remainingEnergy);
        CombatCounters countersWithSpend = state.counters().observing(List.of(energySpent));

        EffectResolver.Resolution resolution = effectResolver.resolve(
                state.combatId(),
                resultingRevision,
                paidCombatants,
                state.timeline(),
                afterTransition,
                actor.combatantId(),
                targets.targetIds(),
                definition,
                java.util.List.of(),
                List.of(),
                countersWithSpend,
                null,
                1);

        CombatantState resolvedActor = findCombatant(resolution.combatants(), actor.combatantId());
        List<CombatEvent> vigorEvents = new ArrayList<>();
        CombatantState spentActor = spendCritical(
                state.combatId(), resultingRevision,
                spendVigor(
                        state.combatId(), resultingRevision, resolvedActor, definition,
                        resolution.events(), paidActor.statuses(), vigorEvents),
                definition, resolution.events(), vigorEvents);
        List<CombatantState> combatantsAfterVigor = replaceCombatant(
                resolution.combatants(), actor.combatantId(), spentActor);

        // A move is answered target by target once it has landed, exactly as a played card.
        FollowUpResolution answered = answerTheTargets(
                state.combatId(), resultingRevision, combatantsAfterVigor, resolution.timeline(),
                resolution.random(), actor.combatantId(), targets.targetIds(), definition);

        // The relics of whoever this move was aimed at, exactly as a human's card play asks them.
        // An enemy move never goes through playCard -- it is spent off a pattern, not out of a
        // hand -- so a relic that watches the other side has to be asked here as well, or it
        // would answer duels and never answer a run.
        List<CombatEvent> opponentRelicEvents = new ArrayList<>();
        fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep afterOpponentRelics =
                relicEngine.processOpponentCardPlayed(
                        spentActor,
                        definition,
                        state.counters(),
                        answered.combatants(),
                        answered.timeline(),
                        answered.random(),
                        state.combatId(),
                        resultingRevision,
                        opponentRelicEvents);

        List<CombatantState> resultingCombatants = afterOpponentRelics.combatants();

        FollowUpResolution followUps = applyCardFollowUps(
                state.combatId(),
                resultingRevision,
                resultingCombatants,
                afterOpponentRelics.timeline(),
                afterOpponentRelics.random(),
                actor.combatantId(),
                targets.targetIds().isEmpty() ? null : targets.targetIds().getFirst(),
                definition.type(),
                false);
        List<CombatEvent> imitationEvents = new ArrayList<>();
        resultingCombatants = imitate(
                state.combatId(), resultingRevision, followUps.combatants(),
                targets.targetIds(), command.cardId(), imitationEvents);

        fr.insastronaute.webapi.sts.combat.event.CardPlayed cardPlayed =
                new fr.insastronaute.webapi.sts.combat.event.CardPlayed(
                        state.combatId(),
                        resultingRevision,
                        actor.combatantId(),
                        move.instanceId(),
                        command.cardId(),
                        targets.targetIds());
        List<CombatEvent> playEvents = List.of(cardPlayed, energySpent);
        List<CombatEvent> events = new ArrayList<>(playEvents);
        events.addAll(provocation.events());
        events.addAll(resolution.events());
        events.addAll(vigorEvents);
        events.addAll(answered.events());
        events.addAll(opponentRelicEvents);
        events.addAll(followUps.events());
        events.addAll(imitationEvents);
        PowerPassiveEngine.Result cardPowers = powerPassiveEngine.onCardPlayed(
                state.combatId(), resultingRevision, cardPlayed, definition, move, resultingCombatants);
        resultingCombatants = cardPowers.combatants();
        events.addAll(cardPowers.events());
        List<CombatEvent> sapEvents = new ArrayList<>();
        resultingCombatants = biteWithSap(
                state.combatId(), resultingRevision, resultingCombatants, sapEvents);
        events.addAll(sapEvents);

        StatusEventReactionResult statusEventReactions = applyStatusEventReactions(
                state.combatId(), resultingRevision, resultingCombatants, events, 0);
        resultingCombatants = statusEventReactions.combatants();
        events.addAll(statusEventReactions.events());
        List<TimelineEntry> timelineAfterStatusEvents = applyCompilationDelay(
                resultingCombatants, followUps.timeline(), state.activeCombatantId(), events, 0);
        // Comme dans playCard : un ennemi porteur d'une Distorsion temporelle en répand une à
        // chaque coup de son motif, et le flux repart d'où elle le laisse.
        Distorted distorted = applyTimeDistortion(
                resultingCombatants, timelineAfterStatusEvents, followUps.random(),
                actor.combatantId(), definition);
        timelineAfterStatusEvents = distorted.timeline();

        CombatProgress outcome = outcomeOf(resultingCombatants, timelineAfterStatusEvents);
        // Même règle que playCard : la dépense est déjà comptée, et chaque événement émis par
        // les suiveurs doit être compté une fois.
        CombatCounters counters = resolution.counters()
                .observing(answered.events())
                .observing(opponentRelicEvents)
                .observing(followUps.events()).observing(imitationEvents)
                .observing(cardPowers.events()).observing(sapEvents).afterAction();
        if (outcome.finished()) {
            return transition(
                    state,
                    resultingRevision,
                    distorted.random(),
                    resultingCombatants,
                    timelineAfterStatusEvents,
                    state.activeCombatantId(),
                    events,
                    counters);
        }

        // An authored move can kill its own caster too, or delay it past an ally still standing,
        // and either leaves the combat running. Same fault as playing a card, same answer: the
        // turn stops on the caster that lost it and goes to the first living entry.
        if (!holdsTheTurn(resultingCombatants, outcome.timeline(), state.activeCombatantId())) {
            return handOverTheLostTurn(
                    state,
                    resultingRevision,
                    resultingCombatants,
                    outcome.timeline(),
                    distorted.random(),
                    counters,
                    events);
        }

        return transition(
                state,
                resultingRevision,
                distorted.random(),
                resultingCombatants,
                timelineAfterStatusEvents,
                state.activeCombatantId(),
                events,
                counters);
    }

    /**
     * Dissipe la Vigueur quand la carte qui vient d'être jouée s'en est servie.
     *
     * <p>Unity le décide depuis le statut lui-même : le modificateur note qu'il s'est appliqué,
     * et {@code OnCardPlayed} le fait expirer ensuite (VigorStatus.cs:27). Rien ici ne peut
     * fonctionner ainsi — un modificateur est pur et se voit demander la même valeur deux fois —
     * alors on lit après coup ce que la carte a fait : un effet de dégâts porté par le lanceur
     * est exactement le moment où le modificateur se serait déclaré appliqué.
     *
     * <p>Une carte de suivi ne consomme pas la Vigueur, faute de l'avoir reçue.
     */
    /** La file et le flux tels que la Distorsion temporelle les laisse. */
    private record Distorted(List<TimelineEntry> timeline, RandomState random) {
    }

    /**
     * Pushes somebody's next turn back, because a card was played.
     *
     * <p>Distorsion temporelle : chaque carte que son porteur joue repousse le prochain tour d'un
     * combattant tiré au sort, d'une fraction elle aussi tirée au sort, jusqu'à la moitié de son
     * délai. Le tirage prend tout le monde, le porteur compris -- mais ce qui lui retombe dessus
     * ne compte que pour moitié, ce qui n'est écrit nulle part côté joueur.
     *
     * <p>Deux tirages, pris sur le flux du combat comme tout le reste : qui, puis combien.
     * L'entrée repoussée est la prochaine du combattant, jamais celle qui est en train de se
     * jouer -- on ne repousse pas un tour déjà commencé.
     */
    private static Distorted applyTimeDistortion(
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            String casterId,
            CardDefinition definition
    ) {
        CombatantState caster = findCombatant(combatants, casterId);
        if (caster == null
                || definition.hasTag(fr.insastronaute.webapi.sts.combat.catalog.CardTag.FOLLOW_UP)
                || caster.statuses().stream()
                        .noneMatch(s -> TIME_DISTORTION.equals(s.statusType()))) {
            return new Distorted(timeline, random);
        }
        List<CombatantState> living = combatants.stream()
                .filter(combatant -> combatant.hp() > 0)
                .toList();
        if (living.isEmpty()) {
            return new Distorted(timeline, random);
        }

        RandomState.RandomStep who = random.nextInt(living.size());
        CombatantState victim = living.get(who.value());
        // Le pourcentage est tiré dans [1, 50] : une distorsion qui ne distord rien ne vaut pas
        // l'événement qu'elle ferait envoyer.
        RandomState.RandomStep howMuch = who.next().nextInt(MAX_TIME_DISTORTION_PERCENT);
        int percent = howMuch.value() + 1;
        if (victim.combatantId().equals(casterId)) {
            percent = Math.max(1, percent / 2);
        }

        TimelineEntry next = timeline.stream()
                .filter(entry -> entry.combatantId().equals(victim.combatantId()))
                .filter(entry -> !entry.equals(timeline.isEmpty() ? null : timeline.getFirst()))
                .min(java.util.Comparator.comparingLong(TimelineEntry::readyAtTick))
                .orElse(null);
        if (next == null) {
            return new Distorted(timeline, howMuch.next());
        }
        long pushed = Math.max(1L, (long) victim.turnDelayTicks() * percent / 100);

        // Rien n'est annoncé : un tour repoussé ne l'est jamais, pas même par l'effet DelayTurn,
        // et le client lit la file dans l'instantané qui suit.
        List<TimelineEntry> updated = new ArrayList<>();
        for (TimelineEntry entry : timeline) {
            updated.add(entry.equals(next)
                    ? new TimelineEntry(entry.combatantId(),
                            Math.addExact(entry.readyAtTick(), pushed), entry.sequence())
                    : entry);
        }
        return new Distorted(TimelineOperations.sorted(updated), howMuch.next());
    }

    /**
     * Takes one charge off "Critique", the status that doubles its holder's next few blows.
     *
     * <p>Une charge par carte qui a effectivement frappé, et non par coup : une carte qui touche
     * trois cibles est une attaque, pas trois. Une carte qui n'a blessé personne n'en coûte
     * aucune, pour la même raison que la Vigueur ne se dépense pas sans dégâts -- on ne paie que
     * ce qui a servi.
     *
     * <p>Une carte de suivi ne la dépense pas davantage que la Vigueur : elle n'a pas été jouée.
     */
    private static CombatantState spendCritical(
            java.util.UUID combatId,
            long revision,
            CombatantState actor,
            CardDefinition definition,
            List<CombatEvent> resolutionEvents,
            List<CombatEvent> events
    ) {
        if (definition.hasTag(fr.insastronaute.webapi.sts.combat.catalog.CardTag.FOLLOW_UP)) {
            return actor;
        }
        boolean dealtDamage = resolutionEvents.stream()
                .anyMatch(event -> event instanceof fr.insastronaute.webapi.sts.combat.event.DamageApplied damage
                        && damage.sourceId().equals(actor.combatantId()));
        if (!dealtDamage) {
            return actor;
        }
        List<StatusInstance> remaining = new ArrayList<>();
        boolean spent = false;
        for (StatusInstance status : actor.statuses()) {
            if (spent || !CRITICAL.equals(status.statusType()) || status.value() <= 0) {
                remaining.add(status);
                continue;
            }
            spent = true;
            int left = status.value() - 1;
            events.add(new fr.insastronaute.webapi.sts.combat.event.StatusRemoved(
                    combatId, revision, actor.combatantId(), status.statusType(), 1, left, 0,
                    left > 0 ? "spent" : "expiry"));
            if (left > 0) {
                remaining.add(new StatusInstance(status.statusType(), left, status.duration(),
                        status.cardId(), status.index(), status.removable(),
                        status.resistsEverything(), status.progress()));
            }
        }
        return spent ? withStatuses(actor, remaining) : actor;
    }

    private static CombatantState spendVigor(
            java.util.UUID combatId,
            long revision,
            CombatantState actor,
            CardDefinition definition,
            List<CombatEvent> resolutionEvents,
            List<StatusInstance> statusesBeforeCard,
            List<CombatEvent> events
    ) {
        if (definition.hasTag(fr.insastronaute.webapi.sts.combat.catalog.CardTag.FOLLOW_UP)) {
            return actor;
        }
        boolean dealtDamage = resolutionEvents.stream()
                .anyMatch(event -> event instanceof fr.insastronaute.webapi.sts.combat.event.DamageApplied damage
                        && damage.sourceId().equals(actor.combatantId()));
        if (!dealtDamage) {
            return actor;
        }
                int vigorToSpend = statusesBeforeCard.stream()
                                .filter(status -> VIGOR.equals(status.statusType()))
                                .mapToInt(StatusInstance::value)
                                .sum();
                if (vigorToSpend <= 0) {
                        return actor;
                }
        List<StatusInstance> remaining = new ArrayList<>();
        boolean spent = false;
        for (StatusInstance status : actor.statuses()) {
                        if (!VIGOR.equals(status.statusType())) {
                remaining.add(status);
                continue;
            }
                        int consumed = Math.min(vigorToSpend, status.value());
                        vigorToSpend -= consumed;
                        if (consumed == 0) {
                                remaining.add(status);
                                continue;
                        }
                        spent = true;
            events.add(new fr.insastronaute.webapi.sts.combat.event.StatusRemoved(
                    combatId, revision, actor.combatantId(), status.statusType(),
                                        consumed, 0, 0, "expiry"));
                        int retained = status.value() - consumed;
                        if (retained > 0) {
                                StatusInstance retainedVigor = new StatusInstance(status.statusType(), retained,
                                                status.duration(), status.cardId(), status.index(), status.removable(),
                                                status.resistsEverything(), status.progress());
                                remaining.add(retainedVigor);
                                events.add(new fr.insastronaute.webapi.sts.combat.event.StatusApplied(
                                                combatId, revision, actor.combatantId(), actor.combatantId(),
                                                retainedVigor.statusType(), retainedVigor.value(), retainedVigor.duration(), true));
                        }
        }
        return spent ? withStatuses(actor, remaining) : actor;
    }

    /**
     * Takes one charge off "Coût nul", the status that makes the next cards its holder plays free.
     *
     * <p>Unity counts it down from inside the cost calculation itself, as a side effect of being
     * asked what the card costs. Nothing here can work that way -- the cost is worked out from
     * immutable state, and asking twice must answer twice the same -- so the charge is spent here
     * instead, at the one moment a card is actually played.
     *
     * <p>A follow-up card is free of charge in both senses: it costs nothing to begin with, and
     * Unity does not let it eat a charge either (CostNullifyStatus.cs:20).
     */
    private static CombatantState spendCostNullifyCharge(
            CombatantState actor, CardDefinition definition) {
        if (definition.hasTag(fr.insastronaute.webapi.sts.combat.catalog.CardTag.FOLLOW_UP)) {
            return actor;
        }
        List<StatusInstance> remaining = new ArrayList<>();
        boolean spent = false;
        for (StatusInstance status : actor.statuses()) {
            if (spent || !COST_NULLIFY.equals(status.statusType()) || status.value() <= 0) {
                remaining.add(status);
                continue;
            }
            spent = true;
            int left = status.value() - 1;
            if (left > 0) {
                remaining.add(new StatusInstance(status.statusType(), left, status.duration(),
                        status.cardId(), status.index()));
            }
        }
        return spent ? withStatuses(actor, remaining) : actor;
    }

    /**
     * What playing this card costs right now.    /**
     * What playing this card costs right now.
     *
     * <p>Cost is a number like any other, so the card's own modifiers and its enchantments change
     * it: a card made cheaper this turn is cheaper when the energy is checked and again when it is
     * taken. Package-visible so the confusion that plays a turn in its holder's place can tell
     * what it can afford (ConfusedTurn).
     */
    int costOf(
            CombatState state,
            CombatantState actor,
            CardInstance card,
            CardDefinition definition
    ) {
        if (definition.xCost()) {
            return Math.max(0, actor.energy());
        }
        // A copy priced by whatever conjured it starts from that price; the card's own modifiers
        // and enchantments then work on it exactly as they would on an authored one.
        int basePrice = card.costOr(definition.cost());
        List<fr.insastronaute.webapi.sts.combat.model.modifier.StatModifier> modifiers =
                java.util.stream.Stream.concat(
                        CardModifierFactory.create(definition.modifiers(), state.counters(),
                                actor.combatantId(), catalog).stream(),
                        enchantmentEngine.getModifiers(card.enchantments()).stream()).toList();
        EffectContext context = new EffectContext(
                state, actor, null, List.of(), definition, false, modifiers, state.timeline(),
                state.combatants());
        return Math.max(0, BattleCalculator.getModifiedValue(
                basePrice,
                fr.insastronaute.webapi.sts.combat.model.modifier.StatType.COST,
                context));
    }

    private static boolean hasValidTargets(
            CombatState state,
            CombatantState actor,
            TargetingMode targetingMode,
            List<String> targetIds
    ) {
        if (targetingMode == TargetingMode.NONE) {
            return targetIds.isEmpty();
        }
        if (targetingMode == TargetingMode.SELF) {
            return targetIds.size() == 1 && actor.combatantId().equals(targetIds.getFirst());
        }
        if (targetingMode == TargetingMode.ANY_PLAYER) {
            if (targetIds.size() != 1) {
                return false;
            }
            CombatantState target = findCombatant(state, targetIds.getFirst());
            return target != null
                    && target.hp() > 0
                    && actor.teamId().equals(target.teamId());
        }
        if (targetingMode == TargetingMode.SINGLE_ENEMY) {
            if (targetIds.size() != 1) {
                return false;
            }
            CombatantState target = findCombatant(state, targetIds.getFirst());
            return target != null
                    && target.hp() > 0
                    && !actor.teamId().equals(target.teamId());
        }
        if (targetingMode == TargetingMode.ALL_ENEMIES
                || targetingMode == TargetingMode.RANDOM_ENEMY) {
            return state.combatants().stream()
                    .anyMatch(target -> target.hp() > 0 && !actor.teamId().equals(target.teamId()));
        }
        if (targetingMode == TargetingMode.ALL_CHARACTERS) {
            return state.combatants().stream().anyMatch(target -> target.hp() > 0);
        }
        return false;
    }

    private static TargetResolution resolveTargets(
            CombatState state,
            CombatantState actor,
            TargetingMode targetingMode,
            List<String> requestedTargetIds
    ) {
        return resolveTargets(state.combatants(), actor, targetingMode, requestedTargetIds, state.random());
    }

    private static TargetResolution resolveTargets(
            List<CombatantState> combatants,
            CombatantState actor,
            TargetingMode targetingMode,
            List<String> requestedTargetIds,
            RandomState random
    ) {
        if (targetingMode == TargetingMode.NONE) {
            return new TargetResolution(List.of(actor.combatantId()), random);
        }
                String provocationBearerId = provocationBearerId(combatants, actor, targetingMode);
                if (provocationBearerId != null) {
                        return new TargetResolution(List.of(provocationBearerId), random, provocationBearerId);
                }
        if (targetingMode == TargetingMode.SELF
                || targetingMode == TargetingMode.ANY_PLAYER
                || targetingMode == TargetingMode.SINGLE_ENEMY) {
            // Confusion : la cible demandée n'est plus celle qui est frappée. Le tirage se fait
            // ici, une fois pour toutes, plutôt que dans chaque effet : une carte confuse doit
            // partir de travers en entier, pas se répartir effet par effet.
            return confused(combatants, actor)
                    ? randomTarget(combatants, requestedTargetIds, random)
                    : new TargetResolution(requestedTargetIds, random);
        }

        List<String> candidates = combatants.stream()
                .filter(target -> target.hp() > 0)
                .filter(target -> targetingMode == TargetingMode.ALL_CHARACTERS
                        || !actor.teamId().equals(target.teamId()))
                .map(CombatantState::combatantId)
                .toList();
        if (targetingMode == TargetingMode.RANDOM_ENEMY) {
            // Personne en face : l'effet ne frappe rien, il ne fait pas tomber le combat.
            // Un tirage sur une liste vide leve, et cette resolution est atteinte depuis les
            // suites de fin de tour -- quand le dernier adversaire vient justement de
            // mourir. Les autres modes rendent deja une liste vide dans ce cas, et
            // randomTarget se garde pareil : c'est le seul chemin qui l'oubliait.
            if (candidates.isEmpty()) {
                return new TargetResolution(List.of(), random);
            }
            RandomState.RandomStep randomStep = random.nextInt(candidates.size());
            return new TargetResolution(List.of(candidates.get(randomStep.value())), randomStep.next());
        }
        return new TargetResolution(candidates, random);
    }

        /**
         * Finds the first living non-caster asking to receive the next eligible card.
         *
         * <p>The roster order is the tie breaker when several opponents provoke at once. It is
         * stable in a saved combat, so a replay makes the same choice without spending randomness.
         */
        private static String provocationBearerId(
                        List<CombatantState> combatants,
                        CombatantState actor,
                        TargetingMode targetingMode
        ) {
                if (targetingMode != TargetingMode.SINGLE_ENEMY
                                && targetingMode != TargetingMode.RANDOM_ENEMY) {
                        return null;
                }
                return combatants.stream()
                                .filter(candidate -> !candidate.combatantId().equals(actor.combatantId()))
                                .filter(candidate -> candidate.hp() > 0)
                                .filter(candidate -> candidate.statuses().stream()
                                                .anyMatch(status -> "Provocation".equals(status.statusType())
                                                                && status.value() > 0))
                                .map(CombatantState::combatantId)
                                .findFirst()
                                .orElse(null);
        }

        /** Consumes exactly one Provocation charge after it actually redirected a card. */
        private static ProvocationSpent spendProvocation(
                        java.util.UUID combatId,
                        long revision,
                        List<CombatantState> combatants,
                        String bearerId
        ) {
                if (bearerId == null) {
                        return new ProvocationSpent(combatants, List.of());
                }
                CombatantState bearer = findCombatant(combatants, bearerId);
                if (bearer == null) {
                        return new ProvocationSpent(combatants, List.of());
                }
                List<StatusInstance> statuses = new ArrayList<>(bearer.statuses());
                for (int index = 0; index < statuses.size(); index++) {
                        StatusInstance status = statuses.get(index);
                        if (!"Provocation".equals(status.statusType()) || status.value() <= 0) {
                                continue;
                        }
                        int remaining = status.value() - 1;
                        if (remaining == 0) {
                                statuses.remove(index);
                        } else {
                                statuses.set(index, new StatusInstance(status.statusType(), remaining, status.duration(),
                                                status.cardId(), status.index(), status.removable(),
                                                status.resistsEverything(), status.progress()));
                        }
                        CombatantState updated = withStatuses(bearer, statuses);
                        return new ProvocationSpent(
                                        replaceCombatant(combatants, bearerId, updated),
                                        List.of(new fr.insastronaute.webapi.sts.combat.event.StatusRemoved(
                                                        combatId, revision, bearerId, status.statusType(), 1, remaining,
                                                        remaining == 0 ? 0 : status.duration(), "consumed")));
                }
                return new ProvocationSpent(combatants, List.of());
        }

        private static StatusEventReactionResult applyStatusEventReactions(
                        java.util.UUID combatId,
                        long revision,
                        List<CombatantState> combatants,
                        List<CombatEvent> observedEvents,
                        int from
        ) {
                List<CombatantState> current = new ArrayList<>(combatants);
                List<CombatEvent> events = new ArrayList<>();
                for (CombatEvent event : observedEvents.subList(from, observedEvents.size())) {
                        if (!(event instanceof fr.insastronaute.webapi.sts.combat.event.StatusApplied applied)) {
                                continue;
                        }
                        StatusInstance appliedStatus = new StatusInstance(
                                        applied.statusType(), applied.value(), applied.duration(), "", 0);
                        if (!fr.insastronaute.webapi.sts.combat.engine.status.StatusCatalog.isDebuff(appliedStatus)) {
                                continue;
                        }
                        CombatantState target = findCombatant(current, applied.combatantId());
                        if (target == null) {
                                continue;
                        }
                        for (CombatantState watcher : List.copyOf(current)) {
                                if (watcher.hp() <= 0 || watcher.teamId().equals(target.teamId())) {
                                        continue;
                                }
                                int armor = watcher.statuses().stream()
                                                .filter(status -> "Analyse".equals(status.statusType()))
                                                .mapToInt(StatusInstance::value)
                                                .sum();
                                if (armor <= 0) {
                                        continue;
                                }
                                CombatantState latest = findCombatant(current, watcher.combatantId());
                                CombatantState updated = new CombatantState(
                                                latest.combatantId(), latest.teamId(), latest.controllerType(),
                                                latest.maxHp(), latest.hp(), Math.addExact(latest.armor(), armor),
                                                latest.energy(), latest.baseEnergy(), latest.turnDelayTicks(), latest.piles(),
                                                latest.statuses(), latest.relics(), latest.intentCardId(),
                                                latest.patternIndex(), latest.relicMemory());
                                current = replaceCombatant(current, updated.combatantId(), updated);
                                events.add(new fr.insastronaute.webapi.sts.combat.event.ArmorGained(
                                                combatId, revision, updated.combatantId(), updated.combatantId(),
                                                armor, armor, updated.armor()));
                        }
                }
                return new StatusEventReactionResult(current, events);
        }

        private List<TimelineEntry> applyCompilationDelay(
                        List<CombatantState> combatants,
                        List<TimelineEntry> timeline,
                        String activeCombatantId,
                        List<CombatEvent> events,
                        int from
        ) {
                long delay = compilationDelayTicks(combatants, timeline, activeCombatantId, events, from);
                if (delay <= 0) {
                        return timeline;
                }
                List<TimelineEntry> updated = new ArrayList<>();
                for (TimelineEntry entry : timeline) {
                        updated.add(entry.combatantId().equals(activeCombatantId)
                                        ? entry.withPendingSelfDelay(delay)
                                        : entry);
                }
                return TimelineOperations.sorted(updated);
        }

        private long compilationDelayTicks(
                        List<CombatantState> combatants,
                        List<TimelineEntry> timeline,
                        String activeCombatantId,
                        List<CombatEvent> events,
                        int from
        ) {
                CombatantState active = findCombatant(combatants, activeCombatantId);
                if (active == null || active.hp() <= 0) {
                        return 0;
                }
                int lossEvents = 0;
                for (CombatEvent event : events.subList(from, events.size())) {
                        if (event instanceof fr.insastronaute.webapi.sts.combat.event.DamageApplied damage
                                        && activeCombatantId.equals(damage.targetId()) && damage.hpLost() > 0) {
                                lossEvents++;
                        } else if (event instanceof fr.insastronaute.webapi.sts.combat.event.HpLost loss
                                        && activeCombatantId.equals(loss.targetId()) && loss.actualLoss() > 0) {
                                lossEvents++;
                        }
                }
                if (lossEvents == 0) {
                        return 0;
                }
                int authoredDelay = active.statuses().stream()
                                .filter(status -> "Compilation".equals(status.statusType()))
                                .mapToInt(StatusInstance::value)
                                .sum();
                if (authoredDelay <= 0) {
                        return 0;
                }
                int delayPercent = Math.max(0, BattleCalculator.getModifiedValue(
                                Math.multiplyExact(authoredDelay, lossEvents),
                                StatType.TURN_MANIPULATION_DELAY,
                                new EffectContext(null, active, active, List.of(active), null, false, List.of(),
                                                timeline, combatants)));
                if (delayPercent <= 0 || !relicEngine.canTurnBeMoved(active, false, false)) {
                        return 0;
                }
                return (long) (Math.max(1f, active.turnDelayTicks()) * delayPercent / 100f);
        }

        /**
         * Efface le prochain tour de chacun des combattants nommés.
     *
     * <p>La même opération que l'effet DeleteNextTurn : le tour n'est pas retiré de la file mais
     * repoussé d'un tour complet, ce qui revient au même pour celui qui le perd et garde une file
     * dans laquelle tout le monde a toujours un tour à venir.
     */
    private static List<TimelineEntry> deleteNextTurns(
            List<TimelineEntry> timeline,
            List<CombatantState> combatants,
            List<String> combatantIds
    ) {
        if (combatantIds.isEmpty()) {
            return timeline;
        }
        List<TimelineEntry> current = timeline;
        for (String combatantId : combatantIds) {
            CombatantState combatant = findCombatant(current == null ? List.of() : combatants,
                    combatantId);
            if (combatant == null) {
                continue;
            }
            TimelineEntry next = null;
            for (TimelineEntry entry : current) {
                if (entry.combatantId().equals(combatantId)
                        && (next == null || entry.readyAtTick() < next.readyAtTick())) {
                    next = entry;
                }
            }
            if (next == null) {
                continue;
            }
            List<TimelineEntry> rebuilt = new ArrayList<>();
            for (TimelineEntry entry : current) {
                if (!entry.equals(next)) {
                    rebuilt.add(entry);
                }
            }
            rebuilt.add(new TimelineEntry(
                    combatantId,
                    next.readyAtTick() + (long) Math.max(1f, combatant.turnDelayTicks()),
                    next.sequence() + 1));
            current = rebuilt;
        }
        return current;
    }

    /**
     * Rejoue la carte contre les cibles qui l'ont entièrement bloquée, pour Domination.
     *
     * <p>Le second coup se résout comme le premier mais n'annonce pas de {@code CardPlayed} : la
     * carte n'a été jouée qu'une fois, elle a seulement frappé deux fois, et la dire jouée deux
     * fois ferait avancer tout ce qui compte les cartes jouées.
     */
    private EffectResolver.Resolution replayForOverpower(
            CombatState state,
            long revision,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            String actorId,
            CardDefinition definition,
            fr.insastronaute.webapi.sts.combat.model.CardInstance card,
            CombatCounters counters,
            List<String> targetIds,
            List<CombatEvent> events
    ) {
        if (targetIds.isEmpty()) {
            return new EffectResolver.Resolution(combatants, timeline, List.of(), random, null);
        }
        // La redite lit le compteur tel que la première résolution l'a laissé : le prix de la
        // carte y est déjà compté, comme dans Unity où l'énergie est dépensée avant les effets.
        EffectResolver.Resolution repeat = effectResolver.resolve(
                state.combatId(),
                revision,
                combatants,
                timeline,
                random,
                actorId,
                targetIds,
                definition,
                card.enchantments(),
                List.of(),
                counters,
                card.instanceId(),
                1);
        events.addAll(repeat.events());
        return repeat;
    }

    private record Smoked(List<CombatantState> combatants, RandomState random) {
    }

    /**
     * Fumigène : chaque buff qui s'éteint sur son porteur coûte un point de vie à un ennemi pris
     * au hasard.
     *
     * <p>Le tirage est fait ici et non dans {@code StatusEngine}, qui ne voit qu'un combattant à
     * la fois et n'aurait personne parmi qui choisir. Il se sert du flux aléatoire du combat,
     * pour qu'un combat rejoué désigne le même.
     *
     * @param expiredBuffs combien de buffs viennent de s'éteindre, un point de vie par buff
     */
    private static Smoked applySmoke(
            java.util.UUID combatId,
            long revision,
            List<CombatantState> combatants,
            CombatantState holder,
            int expiredBuffs,
            RandomState random,
            List<CombatEvent> events
    ) {
        if (holder.statuses().stream().noneMatch(status -> "Smoke".equals(status.statusType()))) {
            return new Smoked(combatants, random);
        }
        List<CombatantState> current = new ArrayList<>(combatants);
        RandomState currentRandom = random;
        for (int bite = 0; bite < expiredBuffs; bite++) {
            List<Integer> enemies = new ArrayList<>();
            for (int index = 0; index < current.size(); index++) {
                CombatantState candidate = current.get(index);
                if (candidate.hp() > 0 && holder.teamId() != null
                        && !holder.teamId().equals(candidate.teamId())) {
                    enemies.add(index);
                }
            }
            if (enemies.isEmpty()) {
                break;
            }
            RandomState.RandomStep roll = currentRandom.nextInt(enemies.size());
            currentRandom = roll.next();
            int index = enemies.get(roll.value());
            CombatantState victim = current.get(index);
            CombatantState wounded = new CombatantState(
                    victim.combatantId(), victim.teamId(), victim.controllerType(),
                    victim.maxHp(), victim.hp() - 1, victim.armor(), victim.energy(),
                    victim.baseEnergy(), victim.turnDelayTicks(), victim.piles(),
                    victim.statuses(), victim.relics(), victim.intentCardId(),
                    victim.patternIndex(), victim.relicMemory());
            current.set(index, wounded);
            events.add(new fr.insastronaute.webapi.sts.combat.event.HpLost(
                    combatId, revision, holder.combatantId(), victim.combatantId(),
                    1, 1, wounded.hp()));
        }
        return new Smoked(current, currentRandom);
    }

    /**
     * La Sape mord, à chaque carte jouée par qui que ce soit, chacun de ses porteurs d'un point
     * par instance qu'il porte : elle s'empile comme un Piège, et deux Sapes mordent deux fois.
     *
     * <p>Elle passe l'armure, comme quand elle mordait au tour, mais pas les statuts qui
     * interceptent une perte de PV. Un suivi n'est pas une carte jouée et ne la réveille pas.
     */
    private static List<CombatantState> biteWithSap(
            java.util.UUID combatId,
            long revision,
            List<CombatantState> combatants,
            List<CombatEvent> events
    ) {
        List<CombatantState> current = new ArrayList<>(combatants);
        for (int index = 0; index < current.size(); index++) {
            CombatantState holder = current.get(index);
            int saps = (int) holder.statuses().stream()
                    .filter(status -> "Sap".equals(status.statusType()))
                    .count();
            if (saps == 0 || holder.hp() <= 0) {
                continue;
            }
            fr.insastronaute.webapi.sts.combat.engine.status.StatusReaction.HpLossVerdict verdict =
                    fr.insastronaute.webapi.sts.combat.engine.status.StatusReaction.validateHpLoss(
                            holder.statuses(), saps, holder.hp(), current);
            int lost = Math.min(holder.hp(), verdict.hpLost());
            CombatantState bitten = new CombatantState(
                    holder.combatantId(), holder.teamId(), holder.controllerType(),
                    holder.maxHp(), holder.hp() - lost, holder.armor(), holder.energy(),
                    holder.baseEnergy(), holder.turnDelayTicks(), holder.piles(),
                    verdict.statuses(), holder.relics(), holder.intentCardId(),
                    holder.patternIndex(), holder.relicMemory());
            current.set(index, bitten);
            events.add(new fr.insastronaute.webapi.sts.combat.event.HpLost(
                    combatId, revision, holder.combatantId(), holder.combatantId(),
                    saps, lost, bitten.hp()));
        }
        return current;
    }

    private record MechaArmed(List<CombatantState> combatants, RandomState random) {
    }

    /**
     * Bras mécatronique : déclaré au catalogue et donné par PowerPassiveEngine, mais jamais
     * implémenté nulle part, si bien qu'il ne se passait jamais rien à la fin du tour de son
     * porteur. Le tirage se fait ici plutôt que dans {@code StatusEngine}, qui ne voit qu'un
     * combattant et n'a personne parmi qui choisir pour la troisième issue.
     */
    private static MechaArmed applyMechaArm(
            java.util.UUID combatId,
            long revision,
            List<CombatantState> combatants,
            CombatantState holder,
            RandomState random,
            List<CombatEvent> events
    ) {
        StatusInstance mechaArm = holder.statuses().stream()
                .filter(status -> "MechaArm".equals(status.statusType()))
                .findFirst()
                .orElse(null);
        if (mechaArm == null || mechaArm.value() <= 0) {
            return new MechaArmed(combatants, random);
        }
        int value = mechaArm.value();
        RandomState.RandomStep roll = random.nextInt(3);
        RandomState currentRandom = roll.next();
        List<CombatantState> current = combatants;
        switch (roll.value()) {
            case 0 -> {
                CombatantState actor = findCombatant(current, holder.combatantId());
                int healed = Math.min(actor.maxHp() - actor.hp(), value);
                CombatantState healedActor = new CombatantState(
                        actor.combatantId(), actor.teamId(), actor.controllerType(), actor.maxHp(),
                        actor.hp() + healed, actor.armor(), actor.energy(), actor.baseEnergy(),
                        actor.turnDelayTicks(), actor.piles(), actor.statuses(), actor.relics(),
                        actor.intentCardId(), actor.patternIndex(), actor.relicMemory());
                current = replaceCombatant(current, actor.combatantId(), healedActor);
                events.add(new fr.insastronaute.webapi.sts.combat.event.HealApplied(
                        combatId, revision, actor.combatantId(), actor.combatantId(), value, healed,
                        healedActor.hp()));
            }
            case 1 -> {
                CombatantState actor = findCombatant(current, holder.combatantId());
                int armor = actor.armor() + value * 4;
                CombatantState armoredActor = new CombatantState(
                        actor.combatantId(), actor.teamId(), actor.controllerType(), actor.maxHp(),
                        actor.hp(), armor, actor.energy(), actor.baseEnergy(), actor.turnDelayTicks(),
                        actor.piles(), actor.statuses(), actor.relics(), actor.intentCardId(),
                        actor.patternIndex(), actor.relicMemory());
                current = replaceCombatant(current, actor.combatantId(), armoredActor);
                events.add(new fr.insastronaute.webapi.sts.combat.event.ArmorGained(
                        combatId, revision, actor.combatantId(), actor.combatantId(), value * 4,
                        value * 4, armor));
            }
            default -> {
                List<CombatantState> withDamage = new ArrayList<>(current);
                for (int index = 0; index < withDamage.size(); index++) {
                    CombatantState candidate = withDamage.get(index);
                    if (candidate.hp() <= 0 || holder.teamId() == null
                            || holder.teamId().equals(candidate.teamId())) {
                        continue;
                    }
                    int loss = Math.min(candidate.hp(), value * 3);
                    CombatantState wounded = new CombatantState(
                            candidate.combatantId(), candidate.teamId(), candidate.controllerType(),
                            candidate.maxHp(), candidate.hp() - loss, candidate.armor(),
                            candidate.energy(), candidate.baseEnergy(), candidate.turnDelayTicks(),
                            candidate.piles(), candidate.statuses(), candidate.relics(),
                            candidate.intentCardId(), candidate.patternIndex(), candidate.relicMemory());
                    withDamage.set(index, wounded);
                    events.add(new fr.insastronaute.webapi.sts.combat.event.HpLost(
                            combatId, revision, holder.combatantId(), candidate.combatantId(),
                            value * 3, loss, wounded.hp()));
                }
                current = withDamage;
            }
        }
        return new MechaArmed(current, currentRandom);
    }

    /**
     * Offre aux reliques du mourant le mot qu'elles auraient eu sous une carte.
     *
     * <p>Une morsure de statut — Poison, Brûlure, Sape — tue sans passer par le handler de
     * dégâts, donc sans que personne ne consulte les reliques : l'Autoréparation, qui rend ses PV
     * « lorsque vos PV atteignent 0 », laissait mourir son porteur dès que le coup fatal venait
     * d'un statut plutôt que d'une carte. Unity l'appelle depuis {@code OnDeath}, qui ne demande
     * pas d'où vient le coup.
     *
     * <p>C'est le même crochet que pour un coup ordinaire, et la même garde le règle : un
     * handler qui répond à une mort teste {@code owner.hp() > 0} et se tait tant que son porteur
     * tient debout.
     *
     * @param before le combattant tel qu'il était avant la morsure, pour en mesurer la taille
     * @param after  le même, une fois mordu
     * @return la table telle que les reliques la laissent, {@code after} y compris
     */
    private List<CombatantState> answerAStatusDeath(
            CombatantState before,
            CombatantState after,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            java.util.UUID combatId,
            long revision,
            List<CombatEvent> events
    ) {
        if (before.hp() <= 0 || after.hp() > 0 || after.relics().isEmpty()) {
            return combatants;
        }
        int bite = before.hp() - after.hp();
        if (bite <= 0) {
            return combatants;
        }
        fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep answered =
                relicEngine.processDamageTaken(after, bite, combatants, timeline, random,
                        combatId, revision, events);
        List<CombatantState> updated = new ArrayList<>(combatants);
        answered.combatants().forEach(combatant -> {
            for (int index = 0; index < updated.size(); index++) {
                if (updated.get(index).combatantId().equals(combatant.combatantId())) {
                    updated.set(index, combatant);
                    return;
                }
            }
        });
        return updated;
    }

    /** @return true when this combatant no longer chooses who it strikes */
    private static boolean confused(List<CombatantState> combatants, CombatantState actor) {
        CombatantState current = combatants.stream()
                .filter(combatant -> combatant.combatantId().equals(actor.combatantId()))
                .findFirst()
                .orElse(actor);
        return current.statuses().stream()
                .anyMatch(status -> "Confusion".equals(status.statusType()));
    }

    /**
     * Tire au sort une cible parmi tous les vivants, sans égard pour les bords.
     *
     * <p>Confusion égarait autrefois le coup sans lui faire traverser la ligne : une carte qui
     * visait un ennemi en frappait un autre, jamais son lanceur. Dans un duel, où chaque bord ne
     * compte qu'une tête, ce tirage n'avait rien à choisir et le statut ne faisait rien du tout.
     * Il tire donc maintenant parmi tous les combattants debout, celui qui joue la carte compris :
     * une attaque confuse peut se retourner contre lui, et un soin confus peut aller à
     * l'adversaire. C'est le prix d'un statut qui veut dire quelque chose à deux.
     */
    private static TargetResolution randomTarget(
            List<CombatantState> combatants,
            List<String> requestedTargetIds,
            RandomState random
    ) {
        List<String> candidates = combatants.stream()
                .filter(target -> target.hp() > 0)
                .map(CombatantState::combatantId)
                .toList();
        if (candidates.isEmpty()) {
            return new TargetResolution(requestedTargetIds, random);
        }
        RandomState.RandomStep roll = random.nextInt(candidates.size());
        return new TargetResolution(List.of(candidates.get(roll.value())), roll.next());
    }

    /**
     * Counts the card just played against every CardFollowUp and AnyCardFollowUp on the field,
     * and plays whatever fires — mirroring {@code Character.AfterAction} and
     * {@code FollowUpStatus.IncrementFollowUp} (CombatManager.cs / FollowUpStatus.cs).
     *
     * <p>A card born of a follow-up never counts towards another one, so nothing here can chain
     * into itself.
     *
     * @param defaultTargetId what a follow-up needing a single target aims at: the card just
     *                        played's own target for the caster's own CardFollowUp, or the caster
     *                        for everyone else's AnyCardFollowUp
     */
    private FollowUpResolution applyCardFollowUps(
            java.util.UUID combatId,
            long revision,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            String actorId,
            String defaultTargetId,
            fr.insastronaute.webapi.sts.combat.catalog.CardType playedType,
            boolean playedCardIsFollowUp
    ) {
        List<CombatEvent> events = new ArrayList<>();
        if (playedCardIsFollowUp) {
            return new FollowUpResolution(combatants, timeline, random, events);
        }

        List<fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.Trigger> triggers =
                new ArrayList<>();
        List<CombatantState> current = new ArrayList<>(combatants.size());
        for (CombatantState combatant : combatants) {
            String statusType = combatant.combatantId().equals(actorId)
                    ? fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.CARD_FOLLOW_UP
                    : fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.ANY_CARD_FOLLOW_UP;
            fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.Advance advance =
                    fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.advance(
                            combatant.combatantId(), combatant.statuses(), statusType, playedType, false);
            triggers.addAll(advance.triggers());
            current.add(withStatuses(combatant, advance.statuses()));
        }

        List<TimelineEntry> currentTimeline = timeline;
        RandomState currentRandom = random;
        // CardFollowUp reuses the played card's own target; AnyCardFollowUp aims at whoever
        // played the watched card, mirroring IncrementFollowUp's two call sites.
        java.util.function.Function<
                fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.Trigger, String>
                targetOf = trigger -> fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine
                        .ANY_CARD_FOLLOW_UP.equals(trigger.statusType())
                        ? actorId
                        : defaultTargetId;
        return executeFollowUpTriggers(
                combatId, revision, current, currentTimeline, currentRandom, triggers, targetOf, events);
    }

    /**
     * Advances a combatant's own {@code FieldTurnFollowUp} statuses against a field turn just
     * ending, and every combatant's against its own end (FieldTurnFollowUpStatus.cs / Character
     * .FieldTurnEnd, EndTurn).
     */
    private FollowUpResolution applyFieldTurnEndFollowUps(
            java.util.UUID combatId,
            long revision,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            String endingActorId
    ) {
        FollowUpResolution continuous = applyContinuous(
                combatId, revision, combatants, timeline, random);
        combatants = continuous.combatants();
        timeline = continuous.timeline();
        random = continuous.random();
        List<fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.Trigger> triggers =
                new ArrayList<>();
        List<CombatantState> current = new ArrayList<>(combatants.size());
        for (CombatantState combatant : combatants) {
            fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.Advance any =
                    fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.advanceFieldTurnFollowUp(
                            combatant.combatantId(), combatant.statuses(),
                            fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.ANY_FIELD_TURN_END);
            triggers.addAll(any.triggers());
            List<StatusInstance> statuses = any.statuses();
            if (combatant.combatantId().equals(endingActorId)) {
                fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.Advance own =
                        fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine
                                .advanceFieldTurnFollowUp(combatant.combatantId(), statuses,
                                        fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.OWN_TURN_END);
                triggers.addAll(own.triggers());
                statuses = own.statuses();
            }
            current.add(withStatuses(combatant, statuses));
        }
        // Ce que Milieu continu a fait part avec les suivis : on repartait d'une liste vide, et
        // ses coups n'étaient annoncés nulle part -- les PV du porteur tombaient sans un mot.
        return executeFollowUpTriggers(combatId, revision, current, timeline, random, triggers,
                fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.Trigger::holderCombatantId,
                new ArrayList<>(continuous.events()));
    }

        /** Continuous strikes its holder once for every field turn that closes. */
        private FollowUpResolution applyContinuous(
                        java.util.UUID combatId,
                        long revision,
                        List<CombatantState> combatants,
                        List<TimelineEntry> timeline,
                        RandomState random
        ) {
                List<CombatantState> current = new ArrayList<>(combatants);
                List<CombatEvent> events = new ArrayList<>();
                RandomState currentRandom = random;
                for (CombatantState holder : List.copyOf(current)) {
                        if (holder.hp() <= 0) {
                                continue;
                        }
                        List<StatusInstance> continuous = holder.statuses().stream()
                                        .filter(status -> "Continuous".equals(status.statusType()))
                                        .toList();
                        for (StatusInstance status : continuous) {
                                if (status.duration() == 0) {
                                        continue;
                                }
                                CombatantState liveHolder = findCombatant(current, holder.combatantId());
                                if (liveHolder == null || liveHolder.hp() <= 0) {
                                        break;
                                }
                                fr.insastronaute.webapi.sts.combat.engine.effect.ResolutionStep damage =
                                                new fr.insastronaute.webapi.sts.combat.engine.effect.DamageHandler(relicEngine)
                                                                .handle(combatId, revision, liveHolder.combatantId(), liveHolder,
                                                                                new EffectDefinition(fr.insastronaute.webapi.sts.combat.catalog.EffectType.DAMAGE,
                                                                                                status.value(), true),
                                                                                currentRandom, current, timeline, null, List.of());
                                // Recopié combattant par combattant : un pas de dégâts peut ne
                                // porter que ceux qu'il a touchés, et le prendre pour la table
                                // entière faisait disparaître l'autre camp -- le combat se
                                // terminait alors d'un coup, sur une victoire que personne n'avait
                                // remportée.
                                for (CombatantState touched : damage.combatants()) {
                                        current = new ArrayList<>(replaceCombatant(
                                                        current, touched.combatantId(), touched));
                                }
                                timeline = damage.timeline();
                                currentRandom = damage.random();
                                events.addAll(damage.events());

                                CombatantState afterDamage = findCombatant(current, liveHolder.combatantId());
                                if (afterDamage == null) {
                                        continue;
                                }
                                List<StatusInstance> statuses = new ArrayList<>(afterDamage.statuses());
                                int statusIndex = findStatusIndex(statuses, status);
                                if (statusIndex < 0) {
                                        continue;
                                }
                                int duration = status.duration() > 0 ? status.duration() - 1 : status.duration();
                                if (duration == 0) {
                                        statuses.remove(statusIndex);
                                        events.add(new fr.insastronaute.webapi.sts.combat.event.StatusRemoved(
                                                        combatId, revision, afterDamage.combatantId(), status.statusType(),
                                                        status.value(), 0, 0, "expiry"));
                                } else {
                                        statuses.set(statusIndex, new StatusInstance(
                                                        status.statusType(), status.value(), duration, status.cardId(),
                                                        status.index(), status.removable(), status.resistsEverything(),
                                                        status.progress()));
                                }
                                current = replaceCombatant(current, afterDamage.combatantId(),
                                                withStatuses(afterDamage, statuses));
                        }
                }
                return new FollowUpResolution(current, timeline, currentRandom, events);
        }

        private static int findStatusIndex(List<StatusInstance> statuses, StatusInstance wanted) {
                for (int index = 0; index < statuses.size(); index++) {
                        StatusInstance status = statuses.get(index);
                        if (status.statusType().equals(wanted.statusType())
                                        && status.value() == wanted.value()
                                        && status.duration() == wanted.duration()
                                        && status.index() == wanted.index()) {
                                return index;
                        }
                }
                return -1;
        }

    /** Propulsion fires Roquette after every character's completed turn. */
    private FollowUpResolution applyPropulsion(
            java.util.UUID combatId,
            long revision,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random
    ) {
        if (catalog.find("roquette") == null) {
            return new FollowUpResolution(combatants, timeline, random, List.of());
        }
        List<fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.Trigger> triggers =
                combatants.stream()
                        .filter(combatant -> combatant.hp() > 0)
                        .filter(combatant -> combatant.statuses().stream()
                                .anyMatch(status -> "Propulsion".equals(status.statusType())))
                        .map(combatant -> new fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.Trigger(
                                combatant.combatantId(), "Propulsion", "roquette", 0))
                        .toList();
        return executeFollowUpTriggers(combatId, revision, combatants, timeline, random, triggers,
                ignored -> null, new ArrayList<>());
    }

    /**
     * Advances the combatant whose turn just opened against its own {@code FieldTurnFollowUp}
     * statuses (FieldTurnFollowUpStatus.OnTurnStart).
     */
    private FollowUpResolution applyTurnStartFollowUps(
            java.util.UUID combatId,
            long revision,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            String startingActorId
    ) {
        List<fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.Trigger> triggers =
                new ArrayList<>();
        List<CombatantState> current = new ArrayList<>(combatants.size());
        for (CombatantState combatant : combatants) {
            if (!combatant.combatantId().equals(startingActorId)) {
                current.add(combatant);
                continue;
            }
            fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.Advance advance =
                    fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.advanceFieldTurnFollowUp(
                            combatant.combatantId(), combatant.statuses(),
                            fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.OWN_TURN_START);
            triggers.addAll(advance.triggers());
            current.add(withStatuses(combatant, advance.statuses()));
        }
        return executeFollowUpTriggers(combatId, revision, current, timeline, random, triggers,
                fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.Trigger::holderCombatantId,
                new ArrayList<>());
    }

    /**
     * What the statuses on both ends say about each target, once an attack has landed.
     *
     * <p>Unity asks it target by target after the card's effects (CombatManager.cs, after the
     * effect loop): the caster's statuses first ({@code OnCardPlayed} -- Sadism III's extra
     * attack), then the target's ({@code OnTargetedByCard} -- Crystallize's). Asking Crystallize
     * before the attack, as the engine used to, meant the hit that armed it could never be the
     * one that set it off: it took one attack more than the status says.
     *
     * <p>A follow-up answers nothing, so nothing here can chain into itself.
     */
    private FollowUpResolution answerTheTargets(
            java.util.UUID combatId,
            long revision,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            String actorId,
            List<String> targetIds,
            CardDefinition definition
    ) {
        if (definition.type() != fr.insastronaute.webapi.sts.combat.catalog.CardType.ATTACK
                || definition.hasTag(fr.insastronaute.webapi.sts.combat.catalog.CardTag.FOLLOW_UP)) {
            return new FollowUpResolution(combatants, timeline, random, List.of());
        }
        CardDefinition relentless = catalog.find(SADISM_III_ATTACK);
        List<CombatantState> current = combatants;
        List<TimelineEntry> currentTimeline = timeline;
        RandomState currentRandom = random;
        List<CombatEvent> events = new ArrayList<>();
        for (String targetId : targetIds) {
            CombatantState caster = findCombatant(current, actorId);
            CombatantState target = findCombatant(current, targetId);
            if (caster == null || target == null || caster.combatantId().equals(targetId)) {
                continue;
            }
            // Sadisme III : une attaque qui touche un adversaire à la moitié de sa vie ou moins
            // en déclenche une autre (SadismIIIStatus.OnCardPlayed).
            if (relentless != null && caster.hp() > 0
                    && caster.statuses().stream()
                            .anyMatch(status -> "SadismIII".equals(status.statusType()))
                    && (long) target.hp() * 2 <= target.maxHp()) {
                FollowUpResolution fired = executeFollowUpTriggers(
                        combatId, revision, current, currentTimeline, currentRandom,
                        List.of(new fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine
                                .Trigger(actorId, "SadismIII", relentless.id(), 0)),
                        trigger -> targetId, new ArrayList<>());
                current = fired.combatants();
                currentTimeline = fired.timeline();
                currentRandom = fired.random();
                events.addAll(fired.events());
            }
            CombatantState struck = findCombatant(current, targetId);
            if (struck == null || struck.hp() <= 0) {
                continue;
            }
            FollowUpResolution crystallized = applyCrystallizeFollowUps(
                    combatId, revision, current, currentTimeline, currentRandom, actorId,
                    List.of(targetId), definition.type());
            current = crystallized.combatants();
            currentTimeline = crystallized.timeline();
            currentRandom = crystallized.random();
            events.addAll(crystallized.events());
        }
        return new FollowUpResolution(current, currentTimeline, currentRandom, events);
    }

    /** Fires an armed Crystallize once an attack has landed on its holder. */
    private FollowUpResolution applyCrystallizeFollowUps(
            java.util.UUID combatId,
            long revision,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            String actorId,
            List<String> targetIds,
            fr.insastronaute.webapi.sts.combat.catalog.CardType cardType
    ) {
        if (cardType != fr.insastronaute.webapi.sts.combat.catalog.CardType.ATTACK) {
            return new FollowUpResolution(combatants, timeline, random, List.of());
        }
        CardDefinition crystal = catalog.find("Recristallisation");
        if (crystal == null) {
            return new FollowUpResolution(combatants, timeline, random, List.of());
        }
        List<CombatantState> current = combatants;
        List<TimelineEntry> currentTimeline = timeline;
        RandomState currentRandom = random;
        List<CombatEvent> events = new ArrayList<>();
        for (String targetId : targetIds) {
            CombatantState target = findCombatant(current, targetId);
            if (target == null) {
                continue;
            }
            StatusInstance armed = target.statuses().stream()
                    .filter(status -> "Crystallize".equals(status.statusType())
                            && status.progress() > 0)
                    .findFirst()
                    .orElse(null);
            if (armed == null) {
                continue;
            }
                        if (target.statuses().stream()
                                        .anyMatch(status -> "Fragmentation".equals(status.statusType()))) {
                                List<CombatantState> armored = new ArrayList<>(current.size());
                                for (CombatantState combatant : current) {
                                        if (combatant.hp() > 0 && target.teamId() != null
                                                        && !target.teamId().equals(combatant.teamId())) {
                                                CombatantState reinforced = withEnergyPilesAndArmor(combatant,
                                                                combatant.energy(), combatant.piles(), combatant.armor() + 5);
                                                armored.add(reinforced);
                                                events.add(new fr.insastronaute.webapi.sts.combat.event.ArmorGained(
                                                                combatId, revision, target.combatantId(), combatant.combatantId(),
                                                                5, 5, reinforced.armor()));
                                        } else {
                                                armored.add(combatant);
                                        }
                                }
                                current = armored;
                                target = findCombatant(current, targetId);
                        }
            List<StatusInstance> reset = target.statuses().stream()
                    .map(status -> status == armed
                            ? new StatusInstance(status.statusType(), 1, status.duration(),
                                    status.cardId(), status.index(), status.removable(),
                                    status.resistsEverything(), 0)
                            : status)
                    .toList();
            current = replaceCombatant(current, targetId, withStatuses(target, reset));
            EffectResolver.Resolution resolution = effectResolver.resolve(
                    combatId, revision, current, currentTimeline, currentRandom, actorId,
                    List.of(targetId), crystal, List.of());
            current = resolution.combatants();
            currentTimeline = resolution.timeline();
            currentRandom = resolution.random();
            events.add(new CardPlayed(combatId, revision, actorId,
                    GeneratedCardId.fromRandom(currentRandom), crystal.id(), List.of(targetId)));
            events.addAll(resolution.events());
        }
        return new FollowUpResolution(current, currentTimeline, currentRandom, events);
    }

    /** Resolves Jump's landing card after the status tick removes the temporary invulnerability. */
    private FollowUpResolution applyJumpLanding(
            java.util.UUID combatId,
            long revision,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            CombatantState beforeTick,
            CombatantState jumper
    ) {
        if (beforeTick.statuses().stream().noneMatch(status -> "Jump".equals(status.statusType()))) {
            return new FollowUpResolution(combatants, timeline, random, List.of());
        }
        List<CombatEvent> jumpEvents = new ArrayList<>();
        // Le Saut est dépensé par son atterrissage : Unity le marque mustExpire des l'ouverture
        // du tour (JumpStatus.cs:37). Ici il ne s'écoulait pas — sa durée est permanente, donc le
        // décompte de fin de tour le laissait tel quel — et rien ne le retirait ensuite : le
        // porteur atterrissait a chaque tour, et gardait pour toujours l'immunité qui va avec.
        // Retiré avant la carte, pour que l'atterrissage se résolve sur un combattant qui a déjà
        // quitté les airs.
        List<CombatantState> current = replaceCombatant(
                combatants, jumper.combatantId(), landed(jumper, combatId, revision, jumpEvents));
        List<CombatantState> adversaries = current.stream()
                .filter(combatant -> combatant.hp() > 0)
                .filter(combatant -> !combatant.teamId().equals(jumper.teamId()))
                .toList();
        if (adversaries.isEmpty()) {
            // Personne a viser : le Saut retombe quand meme, faute de quoi il attendrait une
            // cible qui ne viendra pas en gardant son immunité.
            return new FollowUpResolution(current, timeline, random, jumpEvents);
        }
        CombatantState target = adversaries.stream()
                .filter(combatant -> combatant.statuses().stream()
                        .anyMatch(status -> "Targeting".equals(status.statusType())))
                .findFirst()
                .orElse(null);
        RandomState currentRandom = random;
        if (target == null) {
            RandomState.RandomStep choice = random.nextInt(adversaries.size());
            target = adversaries.get(choice.value());
            currentRandom = choice.next();
        }
        List<CombatEvent> events = new ArrayList<>(jumpEvents);
        List<StatusInstance> targetStatuses = target.statuses().stream()
                .filter(status -> !"Targeting".equals(status.statusType()))
                .toList();
        if (targetStatuses.size() != target.statuses().size()) {
                        for (StatusInstance status : target.statuses()) {
                                if ("Targeting".equals(status.statusType())) {
                                        events.add(new fr.insastronaute.webapi.sts.combat.event.StatusRemoved(
                                                        combatId, revision, target.combatantId(), status.statusType(), status.value(),
                                                        0, 0, "expiry"));
                                }
                        }
            target = withStatuses(target, targetStatuses);
            current = replaceCombatant(current, target.combatantId(), target);
        }
        CardDefinition landing = catalog.find("Atterrissage");
        if (landing == null) {
            return new FollowUpResolution(current, timeline, currentRandom, events);
        }
        EffectResolver.Resolution resolution = effectResolver.resolve(
                combatId, revision, current, timeline, currentRandom, jumper.combatantId(),
                List.of(target.combatantId()), landing, List.of());
        events.add(new CardPlayed(combatId, revision, jumper.combatantId(),
                GeneratedCardId.fromRandom(resolution.random()), landing.id(), List.of(target.combatantId())));
        events.addAll(resolution.events());
        return new FollowUpResolution(resolution.combatants(), resolution.timeline(),
                resolution.random(), events);
    }

    /** @return le sauteur sans son Saut, l'expiration annoncée au passage */
    private static CombatantState landed(
            CombatantState jumper,
            java.util.UUID combatId,
            long revision,
            List<CombatEvent> events
    ) {
        List<StatusInstance> remaining = new ArrayList<>();
        for (StatusInstance status : jumper.statuses()) {
            if ("Jump".equals(status.statusType())) {
                events.add(new fr.insastronaute.webapi.sts.combat.event.StatusRemoved(
                        combatId, revision, jumper.combatantId(), status.statusType(),
                        status.value(), 0, 0, "expiry"));
                continue;
            }
            remaining.add(status);
        }
        return withStatuses(jumper, remaining);
    }

    /** Plays whatever a follow-up status triggered, folding it into the ongoing resolution. */
    private FollowUpResolution executeFollowUpTriggers(
            java.util.UUID combatId,
            long revision,
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            List<fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.Trigger> triggers,
            java.util.function.Function<
                    fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.Trigger, String>
                    targetOf,
            List<CombatEvent> events
    ) {
        List<CombatantState> current = combatants;
        List<TimelineEntry> currentTimeline = timeline;
        RandomState currentRandom = random;
        for (fr.insastronaute.webapi.sts.combat.engine.status.FollowUpEngine.Trigger trigger : triggers) {
            CombatantState holder = findCombatant(current, trigger.holderCombatantId());
            if (holder == null || holder.hp() <= 0) {
                continue;
            }
            // Une carte nommée et connue se joue telle quelle. Sans nom, ou sous un nom qu'aucune
            // carte ne porte, le suivi joue une carte tirée de tout le jeu, comme Unity
            // (FollowUpStatus -> STSCardDatabase.GetRandomCard). Il tirait dans le pool des
            // effets qui donnent une carte -- le deck du joueur, filtré -- et abandonnait sur un
            // nom inconnu : Coloriage magique rejouait Innovation, et Rift_Adept ne faisait rien.
            String cardId = trigger.cardId();
            CardDefinition followUpCard = cardId == null || cardId.isBlank()
                    ? null
                    : catalog.find(cardId);
            if (followUpCard == null) {
                List<CardDefinition> pool = catalog.followUpPool();
                if (pool.isEmpty()) {
                    continue;
                }
                RandomState.RandomStep draw = currentRandom.nextInt(pool.size());
                currentRandom = draw.next();
                followUpCard = pool.get(draw.value());
            }

            TargetResolution followUpTargets = autoFollowUpTargets(
                    current, holder, followUpCard.targetingMode(), targetOf.apply(trigger), currentRandom);
            if (followUpTargets.targetIds().isEmpty() && followUpCard.targetingMode() != TargetingMode.NONE) {
                continue;
            }

            EffectResolver.Resolution followUpResolution = effectResolver.resolve(
                    combatId,
                    revision,
                    current,
                    currentTimeline,
                    followUpTargets.random(),
                    holder.combatantId(),
                    followUpTargets.targetIds(),
                    followUpCard,
                    List.of());
            current = followUpResolution.combatants();
            currentTimeline = followUpResolution.timeline();
            currentRandom = followUpResolution.random();

            CardPlayed followUpPlayed = new CardPlayed(
                    combatId,
                    revision,
                    holder.combatantId(),
                    GeneratedCardId.fromRandom(currentRandom),
                    followUpCard.id(),
                    followUpTargets.targetIds());
            events.add(followUpPlayed);
            events.addAll(followUpResolution.events());
            CardInstance followUpInstance = new CardInstance(
                    followUpPlayed.cardInstanceId(), followUpCard.id(), List.of())
                    .withTag(fr.insastronaute.webapi.sts.combat.catalog.CardTag.FOLLOW_UP);
            PowerPassiveEngine.Result passiveResult = powerPassiveEngine.onCardPlayed(
                    combatId, revision, followUpPlayed, followUpCard, followUpInstance, current);
            current = passiveResult.combatants();
            events.addAll(passiveResult.events());
        }
        return new FollowUpResolution(current, currentTimeline, currentRandom, events);
    }

        /** Backend counterpart of Unity's CombatManager.AutoCardTargets for follow-up cards. */
        private static TargetResolution autoFollowUpTargets(
                        List<CombatantState> combatants,
                        CombatantState source,
                        TargetingMode targetingMode,
                        String triggerTargetId,
                        RandomState random
        ) {
                if (targetingMode == TargetingMode.ALL_CHARACTERS) {
                        return resolveTargets(combatants, source, targetingMode, List.of(), random);
                }

                List<CombatantState> opponents = combatants.stream()
                                .filter(candidate -> candidate.hp() > 0)
                                .filter(candidate -> !source.teamId().equals(candidate.teamId()))
                                .toList();
                if (source.controllerType() == fr.insastronaute.webapi.sts.combat.model.ControllerType.AI) {
                        return new TargetResolution(
                                        opponents.isEmpty() ? List.of() : List.of(opponents.getFirst().combatantId()), random);
                }

                if (targetingMode == TargetingMode.SINGLE_ENEMY && triggerTargetId != null
                                && opponents.stream().anyMatch(candidate -> candidate.combatantId().equals(triggerTargetId))) {
                        return new TargetResolution(List.of(triggerTargetId), random);
                }
                // Sans adversaire à reprendre -- la carte surveillée visait son lanceur, ou
                // personne -- Unity en tire un au hasard (AutoCardTargets -> RandomEnemy). Rien
                // ne le faisait ici : la carte n'avait aucune cible et le suivi était sauté, si
                // bien qu'une attaque tirée au hasard après une carte jouée sur soi ne se jouait
                // jamais.
                if ((targetingMode == TargetingMode.SINGLE_ENEMY
                                || targetingMode == TargetingMode.RANDOM_ENEMY)
                                && !opponents.isEmpty()) {
                        RandomState.RandomStep pick = random.nextInt(opponents.size());
                        return new TargetResolution(
                                        List.of(opponents.get(pick.value()).combatantId()), pick.next());
                }
                if (targetingMode == TargetingMode.ANY_PLAYER && triggerTargetId != null
                                && combatants.stream().anyMatch(candidate -> candidate.hp() > 0
                                                && source.teamId().equals(candidate.teamId())
                                                && candidate.combatantId().equals(triggerTargetId))) {
                        return new TargetResolution(List.of(triggerTargetId), random);
                }
                if (targetingMode == TargetingMode.SELF) {
                        return new TargetResolution(List.of(source.combatantId()), random);
                }
                return resolveTargets(combatants, source, targetingMode, List.of(), random);
        }


    /**
     * The move somebody forced on this enemy, when it is the one being played.
     *
     * <p>Written by the effect that forces a move and by Imitate, and read here rather than
     * trusted from the command: the state is what says an enemy owes somebody a move.
     */
    private static StatusInstance forcedMove(CombatantState actor, String cardId) {
        return actor.statuses().stream()
                .filter(status -> FORCED_ACTION.equals(status.statusType())
                        && status.cardId().equals(cardId))
                .findFirst()
                .orElse(null);
    }

    /** @return the statuses with one charge taken off {@code forced}, dropping it when spent */
    private static List<StatusInstance> spent(List<StatusInstance> statuses, StatusInstance forced) {
        List<StatusInstance> remaining = new ArrayList<>();
        for (StatusInstance status : statuses) {
            if (status != forced) {
                remaining.add(status);
            } else if (status.value() > 1) {
                remaining.add(new StatusInstance(status.statusType(), status.value() - 1,
                        status.duration(), status.cardId(), status.index()));
            }
        }
        return remaining;
    }

    /**
     * Answers a card that has just landed on somebody carrying Imitate: a player is handed a copy
     * of it, an enemy owes it back on its own turn (ImitateStatus.OnTargetedByCard).
     *
     * @param events appended to in place, with the copies handed out
     */
    private static List<CombatantState> imitate(
            java.util.UUID combatId,
            long revision,
            List<CombatantState> combatants,
            List<String> targetIds,
            String definitionId,
            List<CombatEvent> events
    ) {
        List<CombatantState> current = combatants;
        int copies = 0;
        for (String targetId : targetIds) {
            CombatantState target = findCombatant(current, targetId);
            if (target == null || target.hp() <= 0 || target.statuses().stream()
                    .noneMatch(status -> IMITATE.equals(status.statusType()))) {
                continue;
            }
            if (target.controllerType() == fr.insastronaute.webapi.sts.combat.model.ControllerType.AI) {
                List<StatusInstance> statuses = new ArrayList<>(target.statuses());
                statuses.add(new StatusInstance(FORCED_ACTION, 1, -1, definitionId, 0));
                current = replaceCombatant(current, targetId, withStatuses(target, statuses));
                continue;
            }
            CardInstance copy = new CardInstance(
                    GeneratedCardId.fromContext("imitate", revision, targetId, copies++),
                    definitionId);
            List<CardInstance> hand = new ArrayList<>(target.piles().hand());
            hand.add(copy);
            current = replaceCombatant(current, targetId, withEnergyPilesAndArmor(
                    target,
                    target.energy(),
                    new CardPiles(target.piles().draw(), hand, target.piles().discard(),
                            target.piles().exhaust()),
                    target.armor()));
            events.add(new CardMoved(combatId, revision, targetId, copy.instanceId(),
                    definitionId, NO_PILE, HAND_PILE, hand.size() - 1));
        }
        return current;
    }

    private static CombatantState withStatuses(CombatantState combatant, List<StatusInstance> statuses) {
        return new CombatantState(
                combatant.combatantId(),
                combatant.teamId(),
                combatant.controllerType(),
                combatant.maxHp(),
                combatant.hp(),
                combatant.armor(),
                combatant.energy(),
                combatant.baseEnergy(),
                combatant.turnDelayTicks(),
                combatant.piles(),
                statuses,
                combatant.relics(),
                combatant.intentCardId(),
                combatant.patternIndex(),
                combatant.relicMemory());
    }

    private record FollowUpResolution(
            List<CombatantState> combatants,
            List<TimelineEntry> timeline,
            RandomState random,
            List<CombatEvent> events
    ) {
    }

        private record StatusEventReactionResult(
                        List<CombatantState> combatants,
                        List<CombatEvent> events
        ) {
        }

        private record ProvocationSpent(List<CombatantState> combatants, List<CombatEvent> events) {
        }

        private record TargetResolution(
                        List<String> targetIds,
                        RandomState random,
                        String provocationBearerId
        ) {
                private TargetResolution(List<String> targetIds, RandomState random) {
                        this(targetIds, random, null);
                }

        private TargetResolution {
            targetIds = List.copyOf(targetIds);
        }
    }

    private static CombatantState findCombatant(CombatState state, String combatantId) {
        return findCombatant(state.combatants(), combatantId);
    }

    private static CombatantState findCombatant(List<CombatantState> combatants, String combatantId) {
        return combatants.stream()
                .filter(combatant -> combatant.combatantId().equals(combatantId))
                .findFirst()
                .orElse(null);
    }

    private static CardPiles removeFromHand(CardPiles piles, CardInstance card) {
        List<CardInstance> hand = piles.hand().stream()
                .filter(candidate -> !candidate.instanceId().equals(card.instanceId()))
                .toList();
        return new CardPiles(piles.draw(), hand, piles.discard(), piles.exhaust());
    }

    /**
     * Where a card lands once it has been played, mirroring {@code CombatManager.cs:1643-1660}.
     *
     * <p>A Power never reaches a pile: it stays in play through the status it applied. A card
     * tagged {@code Exhaust} burns instead of being discarded, which 86 of the 318 authored
     * cards rely on — discarding them hands the player a card the game meant to take away.
     */
    private static CardPiles afterPlaying(
            CardPiles piles,
            CardInstance card,
            CardDefinition definition
    ) {
        if (card.typeOr(definition.type())
                == fr.insastronaute.webapi.sts.combat.catalog.CardType.POWER) {
            return piles;
        }
        if (burns(card, definition)) {
            return addToExhaust(piles, card);
        }
        if (EnchantmentCatalog.levelOf(card, EnchantmentCatalog.INFINITY) > 0) {
            CardInstance returned = hasTag(card, definition,
                    fr.insastronaute.webapi.sts.combat.catalog.CardTag.INFINITE)
                    ? card
                    : card.withModifier(new CardModifier(StatType.COST, ModifierKind.FLAT, 1, "", true));
            return addToHand(piles, returned);
        }
        return addToDiscard(piles, card);
    }

    /**
     * Whether this copy of the card burns when played rather than being discarded.
     *
     * <p>Read off the instance as well as the definition, because the tag is not always authored:
     * a card selection can pin Exhaust onto one copy, and a conjured card can be born with it.
     * Reading only the definition sent those copies to the discard pile, where they could be
     * drawn again -- the one thing the tag exists to prevent.
     */
    /**
     * Où cette carte se trouve dans la pile d'épuisement de son porteur, ou -1 si elle n'y est
     * pas. Le rang est cherché plutôt que supposé au sommet : c'est la seule façon de distinguer
     * une carte réellement épuisée d'une carte qui s'est consumée sans y passer.
     */
    private static int exhaustIndexOf(
            List<CombatantState> combatants, String ownerId, CardInstance card) {
        CombatantState owner = findCombatant(combatants, ownerId);
        if (owner == null) {
            return -1;
        }
        List<CardInstance> exhaust = owner.piles().exhaust();
        for (int index = exhaust.size() - 1; index >= 0; index--) {
            if (exhaust.get(index).instanceId().equals(card.instanceId())) {
                return index;
            }
        }
        return -1;
    }

    private static boolean burns(CardInstance card, CardDefinition definition) {
        return hasTag(card, definition, fr.insastronaute.webapi.sts.combat.catalog.CardTag.EXHAUST);
    }

    /** @return whether the marker is on this copy of the card, or on the card itself */
    private static boolean hasTag(
            CardInstance card,
            CardDefinition definition,
            fr.insastronaute.webapi.sts.combat.catalog.CardTag tag
    ) {
        if (card.removedTags().contains(tag)) {
            return false;
        }
        return card.tags().contains(tag) || (definition != null && definition.hasTag(tag));
    }

    private static CardPiles addToExhaust(CardPiles piles, CardInstance card) {
        List<CardInstance> exhaust = new ArrayList<>(piles.exhaust());
        exhaust.add(card);
        return new CardPiles(piles.draw(), piles.hand(), piles.discard(), exhaust);
    }

    private static CardPiles addToDiscard(CardPiles piles, CardInstance card) {
        List<CardInstance> discard = new ArrayList<>(piles.discard());
        discard.add(card);
        return new CardPiles(piles.draw(), piles.hand(), discard, piles.exhaust());
    }

        private static CardPiles addToHand(CardPiles piles, CardInstance card) {
                List<CardInstance> hand = new ArrayList<>(piles.hand());
                hand.add(card);
                return new CardPiles(piles.draw(), hand, piles.discard(), piles.exhaust());
        }

    /**
     * Replaces the three fields a turn or a card play touches, and carries every other field
     * over untouched: an announced intent and an enemy's position in its pattern survive a turn
     * boundary, so the enemy resumes at its next move instead of restarting its pattern.
     */
    private static CombatantState withEnergyPilesAndArmor(
            CombatantState combatant,
            int energy,
            CardPiles piles,
            int armor
    ) {
        return new CombatantState(
                combatant.combatantId(),
                combatant.teamId(),
                combatant.controllerType(),
                combatant.maxHp(),
                combatant.hp(),
                armor,
                energy,
                combatant.baseEnergy(),
                combatant.turnDelayTicks(),
                piles,
                combatant.statuses(),
                combatant.relics(),
                combatant.intentCardId(),
                combatant.patternIndex(), combatant.relicMemory());
    }

    private static List<CombatantState> replaceCombatant(
            List<CombatantState> combatants,
            String combatantId,
            CombatantState replacement
    ) {
        return combatants.stream()
                .map(combatant -> combatant.combatantId().equals(combatantId)
                        ? replacement
                        : combatant)
                .toList();
    }

    private static CombatResult reject(
            CombatState state,
            RejectionCode code,
            String message
    ) {
        return CombatResult.rejected(state, code, message);
    }
}
