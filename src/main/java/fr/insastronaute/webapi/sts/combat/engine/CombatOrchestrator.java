package fr.insastronaute.webapi.sts.combat.engine;

import fr.insastronaute.webapi.sts.combat.ai.EnemyPolicy;
import fr.insastronaute.webapi.sts.combat.command.CombatCommand;
import fr.insastronaute.webapi.sts.combat.command.AiPlayPatternCommand;
import fr.insastronaute.webapi.sts.combat.command.EndTurnCommand;
import fr.insastronaute.webapi.sts.combat.engine.result.CombatResult;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.combat.model.ControllerType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CombatOrchestrator {

    private static final int DEFAULT_MAX_AI_COMMANDS = 1_000;

    private final CombatEngine engine;
    private final EnemyPolicy policy;
    private final int maxAiCommands;

    public CombatOrchestrator(
            CombatEngine engine,
            EnemyPolicy policy
    ) {
        this(engine, policy, DEFAULT_MAX_AI_COMMANDS);
    }

    public CombatOrchestrator(CombatEngine engine, EnemyPolicy policy, int maxAiCommands) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        if (maxAiCommands <= 0) {
            throw new IllegalArgumentException("maxAiCommands must be positive");
        }
        this.maxAiCommands = maxAiCommands;
    }

    /**
     * Plays out every turn its owner is not in charge of: an enemy's, and a confused player's.
     *
     * <p>A confused player is driven from here for the same reason an enemy is -- somebody has to
     * act and it is not going to be the client -- and by the same means: one command, one
     * revision, until the turn has nothing left to spend. Both services already call this after
     * every command, so Confusion takes hold in a run and in a duel without either of them
     * knowing it exists.
     */
    public Advance advanceAi(CombatState state) {
        Objects.requireNonNull(state, "state must not be null");
        CombatState current = state;
        List<CombatEvent> events = new ArrayList<>();
        int commandCount = 0;
        // Combien de cartes la confusion en cours a déjà jetées, et sur qui : le compte repart à
        // zéro dès que la main change de porteur.
        String confusedActorId = null;
        int confusedPlays = 0;

        while (current.status() == CombatStatus.ACTIVE) {
            CombatantState actor = activeCombatant(current);
            boolean drivenByPolicy = actor.controllerType() == ControllerType.AI;
            boolean drivenByConfusion = !drivenByPolicy && ConfusedTurn.isConfused(actor);
            if (!drivenByPolicy && !drivenByConfusion) {
                break;
            }
            if (commandCount >= maxAiCommands) {
                throw new IllegalStateException("AI command limit exceeded");
            }
            if (!actor.combatantId().equals(confusedActorId)) {
                confusedActorId = actor.combatantId();
                confusedPlays = 0;
            }
            CombatCommand command = drivenByConfusion
                    ? Objects.requireNonNull(
                            new ConfusedTurn(engine.catalog(), engine::costOf)
                                    .nextCommand(current, actor, confusedPlays),
                            "confused command must not be null")
                    : Objects.requireNonNull(
                            policy.nextCommand(current),
                            "AI policy command must not be null");
            CombatResult result = engine.handle(current, command);
            if (!result.accepted()) {
                if (!drivenByConfusion) {
                    throw new IllegalStateException("AI policy command was rejected");
                }
                // Une carte que la confusion croyait jouable ne l'était pas -- un coût que le
                // catalogue seul ne dit pas, une cible qui vient de tomber. Le tour s'arrête là
                // plutôt que de bloquer le combat : la confusion prend la main, elle ne la garde
                // pas contre le moteur.
                CombatResult forcedEnd = engine.handle(current, new EndTurnCommand(
                        current.combatId(), current.revision(), actor.combatantId()));
                if (!forcedEnd.accepted()) {
                    throw new IllegalStateException("confused end-turn command was rejected");
                }
                events.addAll(forcedEnd.events());
                current = forcedEnd.state();
                commandCount++;
                continue;
            }
            events.addAll(result.events());
            current = result.state();
            commandCount++;
            if (drivenByConfusion) {
                confusedPlays++;
            }
            if (command instanceof AiPlayPatternCommand
                    && current.status() == CombatStatus.ACTIVE
                    && actor.combatantId().equals(current.activeCombatantId())) {
                CombatResult endTurn = engine.handle(current, new EndTurnCommand(
                        current.combatId(), current.revision(), actor.combatantId()));
                if (!endTurn.accepted()) {
                    throw new IllegalStateException("AI end-turn command was rejected");
                }
                events.addAll(endTurn.events());
                current = endTurn.state();
            }
        }
        return new Advance(updateIntents(current), events);
    }

    public CombatState updateIntents(CombatState state) {
        CombatState current = state;
        for (CombatantState combatant : current.combatants()) {
            if (combatant.controllerType() == ControllerType.AI && combatant.hp() > 0) {
                CombatCommand intentCmd = policy.predictCommand(current, combatant);
                String intentCardId = null;
                if (intentCmd instanceof fr.insastronaute.webapi.sts.combat.command.PlayCardCommand pcc) {
                    intentCardId = combatant.piles().hand().stream()
                            .filter(c -> c.instanceId().equals(pcc.cardInstanceId()))
                            .map(c -> c.definitionId())
                            .findFirst().orElse(null);
                } else if (intentCmd instanceof fr.insastronaute.webapi.sts.combat.command.AiPlayPatternCommand pattern) {
                    // An authored move is named outright, and it is the only thing a pattern enemy
                    // ever plays: leaving it out left every intent empty and the client guessing
                    // from its own copy of the pattern, which knows nothing of a forced move.
                    intentCardId = pattern.cardId();
                }
                
                CombatantState updatedCombatant = combatant.withIntentCardId(intentCardId);
                
                List<CombatantState> updatedCombatants = new ArrayList<>(current.combatants());
                for (int i = 0; i < updatedCombatants.size(); i++) {
                    if (updatedCombatants.get(i).combatantId().equals(combatant.combatantId())) {
                        updatedCombatants.set(i, updatedCombatant);
                        break;
                    }
                }
                current = new CombatState(
                        current.combatId(),
                        current.revision(),
                        current.dataVersion(),
                        current.status(),
                        current.winnerTeamId(),
                        current.random(),
                        updatedCombatants,
                        current.timeline(),
                        current.activeCombatantId(),
                        current.counters()
                );
            }
        }
        return current;
    }

    private static CombatantState activeCombatant(CombatState state) {
        return state.combatants().stream()
                .filter(combatant -> combatant.combatantId()
                        .equals(state.activeCombatantId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "active combatant is missing"));
    }

    public record Advance(CombatState state, List<CombatEvent> events) {

        public Advance {
            Objects.requireNonNull(state, "state must not be null");
            events = List.copyOf(Objects.requireNonNull(events, "events must not be null"));
        }
    }
}
