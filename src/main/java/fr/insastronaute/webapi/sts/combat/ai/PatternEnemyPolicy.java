package fr.insastronaute.webapi.sts.combat.ai;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.command.CombatCommand;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.command.PlayCardCommand;
import fr.insastronaute.webapi.sts.combat.model.CardInstance;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class PatternEnemyPolicy implements EnemyPolicy {

    private static final Logger LOGGER = LoggerFactory.getLogger(PatternEnemyPolicy.class);

    public record EnemyMoveNode(String cardId, List<Integer> nextIndices, int weight) {}
    
    private final CardCatalog catalog;
    private final Map<String, List<EnemyMoveNode>> enemyPatterns;
    /** The combatants already reported as move-less, so one wasted turn is one line, not one per turn. */
    private final Set<String> reportedMoveless = ConcurrentHashMap.newKeySet();

    public PatternEnemyPolicy(CardCatalog catalog, Map<String, List<EnemyMoveNode>> enemyPatterns) {
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
        this.enemyPatterns = Objects.requireNonNull(enemyPatterns, "enemyPatterns must not be null");
    }

    @Override
    public CombatCommand nextCommand(CombatState state) {
        Objects.requireNonNull(state, "state must not be null");
        if (state.status() != CombatStatus.ACTIVE) {
            throw new IllegalArgumentException("combat must be active");
        }
        CombatantState actor = state.combatants().stream()
                .filter(combatant -> combatant.combatantId()
                        .equals(state.activeCombatantId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "active combatant is missing"));
        if (actor.controllerType() != ControllerType.AI || actor.hp() <= 0) {
            throw new IllegalArgumentException("active combatant must be a living AI");
        }

        reportIfMoveless(state, actor);
        return predictCommand(state, actor);
    }

    /**
     * Says once, when it actually happens, that an enemy is spending its turn on nothing.
     *
     * <p>This lives on {@link #nextCommand} rather than on {@link #predictCommand} on purpose.
     * The prediction is the intent shown next to an enemy's head: the orchestrator recomputes it
     * for every living AI on every state the player is sent, so reporting from there would write
     * a line per enemy per refresh and drown the log in the very fight it is meant to explain.
     * {@code nextCommand} is the enemy's real turn, and each turn is a real wasted turn.
     *
     * <p>The turn itself is left alone: the enemy still ends it and the fight stays playable. An
     * enemy with no move is a content defect that makes a whole encounter pointless, which is why
     * it is an error and not a warning — but it is not a reason to break the player's run.
     */
    private void reportIfMoveless(CombatState state, CombatantState actor) {
        List<EnemyMoveNode> pattern = enemyPatterns.get(extractOriginalEnemyId(actor.combatantId()));
        if (pattern != null && !pattern.isEmpty()) {
            return;
        }
        if (!reportedMoveless.add(actor.combatantId())) {
            return;
        }
        LOGGER.error(
                "Enemy '{}' has {} move pattern in combat {}: it will end every turn without acting."
                        + " Check the movePattern of the enemy definition behind this combatant.",
                actor.combatantId(),
                pattern == null ? "no" : "an empty",
                state.combatId());
    }

    @Override
    public CombatCommand predictCommand(CombatState state, CombatantState actor) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(actor, "actor must not be null");

        // A move forced on this enemy comes before its own: it plays that and its pattern waits.
        CombatCommand forced = forcedCommand(state, actor);
        if (forced != null) {
            return forced;
        }

        String originalEnemyId = extractOriginalEnemyId(actor.combatantId());
        List<EnemyMoveNode> pattern = enemyPatterns.get(originalEnemyId);
        
        if (pattern == null || pattern.isEmpty()) {
            return new EndTurnCommand(state.combatId(), state.revision(), actor.combatantId());
        }
        
        // A pattern is a ring: an index past its end wraps around rather than sticking at the
        // first move, which is what an enemy did for the rest of a fight once its pattern ran out.
        int currentIndex = Math.floorMod(actor.patternIndex(), pattern.size());
        
        EnemyMoveNode currentNode = pattern.get(currentIndex);
        CardDefinition definition;
        try {
            definition = catalog.require(currentNode.cardId());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("AI pattern references an unknown card definition", exception);
        }
        
        // What the move costs is not asked. Energy is the player's economy: every enemy is built
        // with none, and a move is theirs by the pattern rather than bought. The two ways a move
        // is written made that invisible -- an inline move is a definition of its own and costs
        // zero, so the question was always answered yes; a move naming a card gets that card,
        // player price and all, so the question was always answered no. Those enemies never acted
        // at all, and never recovered either: ending the turn leaves patternIndex where it was,
        // so the same unaffordable move came up again every turn for the rest of the fight.
        List<String> targets = targets(state, actor, definition.targetingMode());
        if (!targets.isEmpty() || definition.targetingMode() == TargetingMode.NONE) {
            return new fr.insastronaute.webapi.sts.combat.command.AiPlayPatternCommand(
                    state.combatId(),
                    state.revision(),
                    actor.combatantId(),
                    currentNode.cardId(),
                    targets,
                    nextIndex(pattern, currentIndex, state.random()));
        }
        
        return new EndTurnCommand(state.combatId(), state.revision(), actor.combatantId());
    }
    
    /**
     * The move somebody has forced on this enemy, or null when it owes nobody one.
     *
     * <p>Cost is not asked of it: the move was not chosen by the enemy, and refusing it for want
     * of energy would quietly hand the turn back to the pattern it was meant to replace. The
     * pattern index is left unchosen, so nothing steps the graph forward on a move that is not
     * its own.
     */
    private CombatCommand forcedCommand(CombatState state, CombatantState actor) {
        for (fr.insastronaute.webapi.sts.combat.model.StatusInstance status : actor.statuses()) {
            if (!"ForcedAction".equals(status.statusType()) || status.cardId().isBlank()) {
                continue;
            }
            CardDefinition definition = catalog.find(status.cardId());
            if (definition == null) {
                continue;
            }
            List<String> targets = targets(state, actor, definition.targetingMode());
            if (targets.isEmpty() && definition.targetingMode() != TargetingMode.NONE) {
                continue;
            }
            return new fr.insastronaute.webapi.sts.combat.command.AiPlayPatternCommand(
                    state.combatId(), state.revision(), actor.combatantId(), status.cardId(),
                    targets, -1);
        }
        return null;
    }

    /**
     * Where the pattern goes after the move at {@code currentIndex}.
     *
     * <p>Unity reads the successors a move names and picks one by weight, the weight being that
     * of the move being moved to; a move naming no successor hands over to the next one, wrapping
     * at the end ({@code EnemyData.GetNextActionIndex}). The draw is taken from the combat's own
     * stream, so the same state always produces the same fight.
     */
    static int nextIndex(
            List<EnemyMoveNode> pattern,
            int currentIndex,
            fr.insastronaute.webapi.sts.combat.model.RandomState random
    ) {
        EnemyMoveNode current = pattern.get(currentIndex);
        List<Integer> successors = current.nextIndices().stream()
                .filter(index -> index >= 0 && index < pattern.size())
                .toList();
        if (successors.isEmpty()) {
            return (currentIndex + 1) % pattern.size();
        }

        int totalWeight = successors.stream()
                .mapToInt(index -> Math.max(1, pattern.get(index).weight()))
                .sum();
        int roll = random.nextInt(totalWeight).value();
        for (int successor : successors) {
            roll -= Math.max(1, pattern.get(successor).weight());
            if (roll < 0) {
                return successor;
            }
        }
        return successors.getFirst();
    }

    private String extractOriginalEnemyId(String combatantId) {
        // combatantId is like "enemy-0", but we need the actual blueprint ID for the pattern.
        // Wait, enemyPatterns map needs to be keyed by combatantId to be safe!
        return combatantId;
    }

    private List<String> targets(CombatState state, CombatantState actor, TargetingMode mode) {
        return switch (mode) {
            case NONE -> List.of();
            case SELF -> List.of(actor.combatantId());
            // A move that aims at "any player" picks the first living one on the actor's side,
            // matching the deterministic first-living convention used for random targets below.
            case ANY_PLAYER -> living(state, c -> c.teamId().equals(actor.teamId())).stream()
                    .findFirst()
                    .map(List::of)
                    .orElse(List.of());
            case SINGLE_ENEMY -> state.combatants().stream()
                    .filter(c -> c.hp() > 0 && !c.teamId().equals(actor.teamId()))
                    .map(CombatantState::combatantId)
                    .findFirst()
                    .map(List::of)
                    .orElse(List.of());
            // An enemy aims at the living characters opposite it. Answering "nobody" here made
            // every enemy whose move sweeps a side of the fight pass its turn instead.
            case ALL_ENEMIES -> living(state, c -> !c.teamId().equals(actor.teamId()));
            case ALL_CHARACTERS -> living(state, c -> true);
            case RANDOM_ENEMY -> living(state, c -> !c.teamId().equals(actor.teamId())).stream()
                    .findFirst()
                    .map(List::of)
                    .orElse(List.of());
        };
    }

    /**
     * The living combatants on one side of the fight, in roster order.
     *
     * <p>A random target is the first of them rather than a draw: the enemy roster is ordered,
     * Unity's own enemy AI takes the first living character it finds, and spending a random on
     * it would put the server's random stream out of step with a replay for nothing.
     */
    private List<String> living(
            CombatState state,
            java.util.function.Predicate<CombatantState> side
    ) {
        return state.combatants().stream()
                .filter(combatant -> combatant.hp() > 0)
                .filter(side)
                .map(CombatantState::combatantId)
                .toList();
    }
}
