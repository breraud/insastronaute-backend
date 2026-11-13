package fr.insastronaute.webapi.sts.combat.engine;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.engine.relic.RelicEngine;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatCreated;
import fr.insastronaute.webapi.sts.combat.event.CardDrawn;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.event.PileShuffled;
import fr.insastronaute.webapi.sts.combat.event.TurnStarted;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CardPiles;
import fr.insastronaute.webapi.sts.combat.model.CombatSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantSetup;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.RandomState;
import fr.insastronaute.webapi.sts.combat.model.TimelineEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CombatFactory {

    private static final int INITIAL_HAND_SIZE = 5;
    private static final long INITIAL_REVISION = 0;
    private final CardCatalog catalog;
    private final RelicEngine relicEngine;

    public CombatFactory(CardCatalog catalog, RelicEngine relicEngine) {
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
        this.relicEngine = Objects.requireNonNull(relicEngine, "relicEngine must not be null");
    }

    public CombatCreated create(CombatSetup setup) {
        Objects.requireNonNull(setup, "setup");
        if (!catalog.dataVersion().equals(setup.dataVersion())) {
            throw new IllegalArgumentException(
                    "setup dataVersion '" + setup.dataVersion()
                            + "' does not match catalog dataVersion '"
                            + catalog.dataVersion() + "'");
        }
        validateUniqueIds(setup.combatants());
        validateCardDefinitions(setup.combatants());

        RandomState random = new RandomState(setup.seed());
        Map<String, List<CardInstance>> shuffledDecks = new LinkedHashMap<>();
        List<CombatEvent> events = new ArrayList<>();

        for (CombatantSetup combatant : setup.combatants()) {
            PileOperations.ShuffleResult result =
                    PileOperations.shuffle(combatant.deck(), random);
            random = result.random();
            shuffledDecks.put(combatant.combatantId(), result.cards());
            if (result.cards().size() > 1) {
                events.add(new PileShuffled(
                        setup.combatId(),
                        INITIAL_REVISION,
                        combatant.combatantId(),
                        "DRAW",
                        result.cards().stream().map(CardInstance::instanceId).toList()));
            }
        }

        List<TimelineEntry> timeline = createTimeline(setup.combatants());
        String activeCombatantId = timeline.stream()
                .map(TimelineEntry::combatantId)
                .filter(id -> combatantSetup(setup.combatants(), id).startingHp() > 0)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "combat must contain at least one living combatant"));
        TimelineEntry activeTimelineEntry = timeline.stream()
                .filter(entry -> entry.combatantId().equals(activeCombatantId))
                .findFirst()
                .orElseThrow();

        List<CombatantState> combatants = new ArrayList<>();
        for (CombatantSetup combatant : setup.combatants()) {
            boolean active = combatant.combatantId().equals(activeCombatantId);
            List<CardInstance> shuffledDeck = shuffledDecks.get(combatant.combatantId());
            int drawnCount = active ? Math.min(INITIAL_HAND_SIZE, shuffledDeck.size()) : 0;
            List<CardInstance> hand = shuffledDeck.subList(0, drawnCount);
            List<CardInstance> draw = shuffledDeck.subList(drawnCount, shuffledDeck.size());

            combatants.add(new CombatantState(
                    combatant.combatantId(),
                    combatant.teamId(),
                    combatant.controllerType(),
                    combatant.maxHp(),
                    combatant.startingHp(),
                    0,
                    active ? combatant.baseEnergy() : 0,
                    combatant.baseEnergy(),
                    combatant.turnDelayTicks(),
                    new CardPiles(draw, hand, List.of(), List.of()),
                    combatant.startingStatuses(),
                    combatant.relics(),
                    null,
                    combatant.startingPatternIndex(),
                    combatant.relicMemory()));

            if (active) {
                for (int handIndex = 0; handIndex < hand.size(); handIndex++) {
                    events.add(new CardDrawn(
                            setup.combatId(),
                            INITIAL_REVISION,
                            combatant.combatantId(),
                            hand.get(handIndex).instanceId(),
                            hand.get(handIndex).definitionId(),
                            handIndex));
                }
            }
        }

        CombatantSetup activeSetup = combatantSetup(setup.combatants(), activeCombatantId);

        CombatState preState = new CombatState(
                setup.combatId(),
                INITIAL_REVISION,
                catalog.dataVersion(),
                CombatStatus.ACTIVE,
                null,
                random,
                combatants,
                timeline,
                activeCombatantId);

        List<CombatantState> processedCombatants = new ArrayList<>();
        for (CombatantState combatant : combatants) {
            processedCombatants.add(relicEngine.processCombatStart(combatant, preState, catalog, events));
        }
        requireTwoLivingTeamsAfterRelics(processedCombatants);

        // The opening turn is the one turn no hand-over ever opens: this method deals its hand and
        // sets its energy itself. It is owed the start-of-turn relic hook all the same, or every
        // relic that reads "at the start of each turn" sits out the turn its owner notices most --
        // EP opened its owner on the bare five cards and three energy of somebody with no relic at
        // all. Only the combatant who acts first gets it; the others are owed nothing until their
        // own turn opens.
        CombatState afterCombatStart = new CombatState(
                setup.combatId(),
                INITIAL_REVISION,
                catalog.dataVersion(),
                CombatStatus.ACTIVE,
                null,
                random,
                processedCombatants,
                timeline,
                activeCombatantId);
        CombatantState opener = processedCombatants.stream()
                .filter(combatant -> combatant.combatantId().equals(activeCombatantId))
                .findFirst()
                .orElseThrow();
        fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler.TurnStart openingRelics =
                relicEngine.processTurnStart(opener, afterCombatStart, random, events);
        processedCombatants = replaceCombatant(processedCombatants, openingRelics.combatant());
        random = openingRelics.random();
        requireTwoLivingTeamsAfterRelics(processedCombatants);

        // Announced after the relics, the way every later turn announces it: the hand-over runs
        // its own start-of-turn hook before appending TurnStarted, and a client that reads the two
        // in one order everywhere is a client with one rule to follow.
        events.add(new TurnStarted(
                setup.combatId(),
                INITIAL_REVISION,
                activeCombatantId,
                activeTimelineEntry.readyAtTick(),
                activeSetup.baseEnergy()));

        CombatState state = new CombatState(
                setup.combatId(),
                INITIAL_REVISION,
                catalog.dataVersion(),
                CombatStatus.ACTIVE,
                null,
                random,
                processedCombatants,
                timeline,
                activeCombatantId);
        return new CombatCreated(state, events);
    }

    /**
     * Refuses a combat the relics have already decided, rather than handing it back as one to play.
     *
     * <p>{@link CombatSetup} asks this same question of the health it is handed and refuses a side
     * that walks in already dead. Relics run after that answer and can move health, so the answer
     * has to be asked again once they have; asking only before them left the guarantee resting on
     * a pass that is free to break it.
     *
     * <p>Refused, and not ended as everywhere else the engine meets a death. A combat is ended
     * when the damage has already landed on a fight being played, where refusing would strand a
     * live combat mid-revision — which is exactly what stopped a player's combat for good. Nothing
     * has been played here and nothing has been saved, so there is no outcome to preserve; ending
     * it would write an encounter into the run as won or lost without it ever being fought, and
     * the events handed back would open a turn that is never taken and close a combat in the same
     * breath. No shipped {@code onCombatStart} moves health, so arriving here is a defect in a
     * relic handler, and the useful answer to a defect is to name it while it can still be fixed.
     *
     * <p>Naming it is the other half. Left to the state's own invariant this already threw, but it
     * threw from {@code CombatState} saying an active combat needs two living teams — the wreck
     * described, and never what drove into it.
     */
    /** The same list with one combatant swapped for the version a relic just returned. */
    private static List<CombatantState> replaceCombatant(
            List<CombatantState> combatants,
            CombatantState replacement
    ) {
        List<CombatantState> updated = new ArrayList<>(combatants.size());
        for (CombatantState combatant : combatants) {
            updated.add(combatant.combatantId().equals(replacement.combatantId())
                    ? replacement
                    : combatant);
        }
        return updated;
    }

    private static void requireTwoLivingTeamsAfterRelics(List<CombatantState> combatants) {
        long livingTeams = combatants.stream()
                .filter(combatant -> combatant.hp() > 0)
                .map(CombatantState::teamId)
                .distinct()
                .count();
        if (livingTeams < 2) {
            throw new IllegalArgumentException(
                    "a relic decided the combat at its start: fewer than two living teams remain"
                            + " once onCombatStart has run");
        }
    }

    private void validateCardDefinitions(List<CombatantSetup> combatants) {
        for (CombatantSetup combatant : combatants) {
            for (CardInstance card : combatant.deck()) {
                try {
                    catalog.require(card.definitionId());
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException(
                            "combatant '" + combatant.combatantId()
                                    + "' deck references unknown card definition '"
                                    + card.definitionId() + "'",
                            exception);
                }
            }
        }
    }

    private static void validateUniqueIds(List<CombatantSetup> combatants) {
        Set<String> combatantIds = new HashSet<>();
        Set<String> cardInstanceIds = new HashSet<>();

        for (CombatantSetup combatant : combatants) {
            if (!combatantIds.add(combatant.combatantId())) {
                throw new IllegalArgumentException("combatant IDs must be globally unique");
            }
            for (CardInstance card : combatant.deck()) {
                if (!cardInstanceIds.add(card.instanceId())) {
                    throw new IllegalArgumentException("card instance IDs must be globally unique");
                }
            }
        }
    }

    private static List<TimelineEntry> createTimeline(List<CombatantSetup> combatants) {
        List<TimelineEntry> timeline = new ArrayList<>();
        for (int index = 0; index < combatants.size(); index++) {
            CombatantSetup combatant = combatants.get(index);
            timeline.add(new TimelineEntry(
                    combatant.combatantId(), combatant.initialReadyAtTick(), index));
        }
        return TimelineOperations.sorted(timeline);
    }

    private static CombatantSetup combatantSetup(
            List<CombatantSetup> combatants,
            String combatantId
    ) {
        return combatants.stream()
                .filter(combatant -> combatant.combatantId().equals(combatantId))
                .findFirst()
                .orElseThrow();
    }
}
